# Server-side idempotency for `nyOppgave` / `nyBeskjed` / `nySak`

**Status:** Design proposal — for team decision, no code written yet
**Author:** drafted with Claude, 2026-06-01
**Related:** [postmortem 2026-05-18](../postmortems/duplikater_etter_kafka_outage_2026-05-18.md), Kafka outage 2026-06-01

## Background

We have produced duplicate notifications after a Kafka cluster rebalance **four times** (7 Sep 2022, PR #800, 15–17 May 2026, 1 Jun 2026 — see the `brokenHendelseId` skip-list in `HendelsesstrømKafkaImpl`). The 18 May postmortem named two root causes but only fixed one:

1. ✅ **Fixed:** `MAX_BLOCK_MS` lowered 30s → 5s so the producer fails inside the client's timeout window.
2. ❌ **Deferred:** *"Manglende idempotens-håndtering ved mottak."* — no dedup that survives a client retry.

This doc designs the deferred fix. Note the May change made (1) *worse* in one respect: failing faster means clients retry sooner, which produces *more* duplicates while (2) is unfixed. The June outage is a recurrence of the same unaddressed root cause.

## Problem statement

Idempotency today is a **read-before-produce** check: `hentNotifikasjon(eksternId, merkelapp)` in `MutationNyOppgave.kt:132`. It is blind during this window:

```
[attempt-1 produces H1] ─────── blind window ─────► [H1 visible to hentNotifikasjon]
```

H1 only becomes visible via the inline write in `HendelseDispatcher` (**which runs only after a successful produce**) or via the `produsent-model-builder` consumer (which lags during an outage). During the window a retry reads `null`, generates a fresh `UUID.randomUUID()`, and produces a second event with a **different aggregateId** for the same `(merkelapp, eksternId)` coordinate.

Two consequences:
- **Duplicate notifications** — two `OppgaveOpprettet` for the same coordinate.
- **Consumer poison pill** — the `notifikasjon` table has `constraint unikt_koordinat unique (merkelapp, ekstern_id)` (`V1__init.sql:14`). The consumer's `insert into notifikasjon ... on conflict do nothing` swallows the *coordinate* conflict for the second event, so the `notifikasjon` row with the new id is never inserted, then the unconditional `storeMottaker(...)` FK-violates and the partition retries forever.

The core difficulty (correctly identified by the team): **we must write to both Postgres and Kafka, and there is no atomic commit across the two.** `HendelseDispatcher.send` does Kafka-first, then DB. Any ordering leaves a failure window; the design is about *which* window and *how we reconcile it.*

## Constraints (decided)

- **`hendelseId` / `notifikasjonId` generation must not change.** Still `UUID.randomUUID()`, still `notifikasjonId == hendelseId`.
- **Poison-pill retry-forever stays.** We do *not* want to go unhealthy on a poison pill. (Both designs below remove the *source* of this particular poison pill, but the consumer retry loop is untouched.)
- **We do** want to go unhealthy on the invisible synchronous `send()` throw — see [Cross-cutting fix](#cross-cutting-the-synchronous-send-throw).

## The shared idea: persist and reuse the random id

The constraint above is satisfied by both designs because neither *derives* the id from the coordinate. We keep `UUID.randomUUID()`, but **store the assigned id** in a DB row keyed by `(merkelapp, eksternId)` and **reuse it on retry** instead of minting a new one.

Effect: a retry re-produces an event with the **same** id. If attempt-1's H1 actually landed, the consumer's `on conflict do nothing` dedupes the second copy cleanly on the **primary key** (and mottakere on their unique indexes) → no duplicate notification, no FK poison pill — without changing id generation. Re-delivering the same `hendelseId` is not a new hazard: consumers already get at-least-once redelivery today via the `commitSync` seek-back-and-reprocess path, so they must already be idempotent on `hendelseId`. *(Worth a quick audit of the BigQuery/eksternvarsling consumers to confirm.)*

---

## Option A — Reservation (reserve → produce → confirm)

Smaller change. Reverse the dispatcher order: claim the coordinate in Postgres first, then produce.

### Schema

```sql
-- V3x__notifikasjon_idempotens.sql
create table notifikasjon_idempotens (
    merkelapp   text not null,
    ekstern_id  text not null,
    hendelse_id uuid not null,          -- the assigned random UUID, reused on retry
    status      text not null,          -- 'PENDING' | 'PRODUCED'
    hendelse    jsonb not null,         -- full payload, needed to re-drive
    opprettet   timestamptz not null default now(),
    primary key (merkelapp, ekstern_id)
);
```

### Flow (per create-mutation)

```
1. txn: insert into notifikasjon_idempotens(...PENDING...) on conflict do nothing;  read row back
2. row.status == PRODUCED?
     → compare content: identical → return Vellykket(row.hendelse_id) ("duplisert")
                         differ    → DuplikatEksternIdOgMerkelapp(row.hendelse_id)
3. row.status == PENDING (we won, or a prior attempt stalled):
     → produce(hendelse with row.hendelse_id)        // reuse stored id
     → on success: update status='PRODUCED'; inline oppdaterModellEtterHendelse
     → return Vellykket(row.hendelse_id)
```

### Failure modes

| Failure point | State left | Recovery | Loss? |
|---|---|---|---|
| Crash after step 1 commit, before produce | PENDING, no event | retry re-produces (still PENDING); sweeper if client gives up | No* |
| Produce times out (ambiguous) | PENDING | retry re-produces same id; if H1 landed → consumer PK-dedup | No |
| Produce ok, crash before `PRODUCED` | PENDING, event landed | retry/sweeper re-produces same id → harmless dup delivery | No |
| Two concurrent first calls | one wins the `on conflict` | loser reads winner's row, produces winner's id | No |

\* **Requires a sweeper** (a `launchProcessingLoop`) that re-drives `PENDING` rows older than N minutes, since a client that gives up leaves an orphan PENDING with no event. Confirm `PRODUCED` once `hentNotifikasjon` materializes.

### nySak (multi-event)
`nySak` sends `SakOpprettet` + `NyStatusSak`. Reservation keys on `(merkelapp, grupperingsid)`; store both payloads (or re-derive the status event). Re-drive must re-produce both. Slightly awkward — the reservation row holds a list, or we reserve on the sak coordinate and store both events.

### Pros / cons
- ➕ Small, localized change; reuses `HendelseDispatcher` shape and existing idempotency-table precedent (`notifikasjon_oppdatering`, sak `idempotence_key`).
- ➕ Closes the blind window; prevents duplicates and the FK poison pill at the source.
- ➖ Kafka stays in the request path → produce timeouts still surface to clients → clients still retry (just safely now).
- ➖ Needs a sweeper, and storing the payload to re-drive means we are *most* of the way to an outbox already.

---

## Option B — Transactional outbox (DB authoritative for "intent to produce")

Bigger change, cleaner end state. The mutation never touches Kafka synchronously.

### Schema

```sql
-- V3x__hendelse_outbox.sql
create table hendelse_outbox (
    id          bigserial primary key,   -- relay order
    hendelse_id uuid not null,
    merkelapp   text,
    ekstern_id  text,
    hendelse    jsonb not null,
    status      text not null default 'UNSENT',   -- 'UNSENT' | 'SENT'
    opprettet   timestamptz not null default now()
);
-- coordinate dedup for create-events only:
create unique index outbox_koordinat on hendelse_outbox(merkelapp, ekstern_id)
    where merkelapp is not null and ekstern_id is not null;
```

### Flow

```
Mutation (one DB txn):
  - (creates) claim coordinate via unique index — conflict → return duplisert/DuplikatEksternId
  - insert outbox row(s) with assigned hendelse_id + payload
  - inline oppdaterModellEtterHendelse (read-model immediately consistent)
  - commit  → return Vellykket(id) immediately, regardless of Kafka state

Relay (launchProcessingLoop, single writer per partition):
  - select * from hendelse_outbox where status='UNSENT' order by id
  - produce(payload with stored hendelse_id) via the existing producer/OrgnrPartitioner
  - mark SENT (at-least-once; consumer PK-dedupes a re-send)
```

### Failure modes

| Failure point | State | Recovery | Loss? |
|---|---|---|---|
| Crash after commit, before relay | UNSENT | relay produces next loop | No |
| Relay produces, crash before SENT | UNSENT, event landed | re-produced same id → consumer PK-dedup | No |
| **Kafka down entirely** | UNSENT accumulates | **mutation still succeeds**; relay drains on recovery | No |
| Two concurrent first calls | unique index | one wins, other → duplisert/error | No |

### nySak (multi-event)
Write both outbox rows in the **same txn**; relay produces them in `id` order → atomic and correctly ordered. Cleanest of the two for multi-event.

### Ordering note
The relay must preserve per-partition (per-orgnr) order. A single-threaded relay reading by `id` is trivially correct (and likely fast enough); a per-partition relay parallelizes if needed. Reuse the existing producer so `OrgnrPartitioner` keeps events on the same partitions as today.

### Pros / cons
- ➕ Strongest idempotency; eliminates the dual-write window by construction.
- ➕ **Removes produce timeouts from the client path entirely** → mutations survive a Kafka outage → removes the client-retry pressure that *creates* duplicates. Directly addresses the actual incident trigger.
- ➕ Multi-event atomicity is natural.
- ➖ New relay loop, ordering/backpressure to reason about.
- ➖ Kafka visibility becomes asynchronous (read-model is still immediately consistent via the inline write).
- ➖ The producer is no longer driven by the request thread — a behavioral shift to communicate to the team.

---

## Side-by-side

| Dimension | A — Reservation | B — Outbox |
|---|---|---|
| Change size / risk | Small | Medium |
| Kafka in request path | Yes | **No** |
| Mutations survive Kafka outage | No (fail/retry) | **Yes** |
| Client-facing produce timeouts | Still happen | **Gone** |
| Lost-write risk | None *with sweeper* | None |
| Reconciliation infra | Sweeper loop | Relay loop |
| Prevents duplicate notifikasjon | Yes | Yes |
| Prevents FK poison pill (create) | Yes | Yes |
| Kafka visibility latency | Synchronous | Async (ms–s) |
| Multi-event (nySak) atomicity | Awkward | Natural |
| Converges toward... | the outbox anyway | — |

## Cross-cutting: the synchronous `send()` throw

Independent of A/B and should ship regardless. In `HendelseProdusentKafkaImpl.suspendingSend`, `KafkaProducer.send()` can throw **synchronously** (metadata `TimeoutException` from `MAX_BLOCK_MS=5000`, `SerializationException`, etc.). That throw bypasses the callback, which is the only place that sets `Health.subsystemAlive[KAFKA] = false` — so the `not present in metadata after 5000 ms` path is invisible to liveness. Wrap the `send(...)` call in a `try/catch` that sets the flag and rethrows, symmetric with the async path.

**Caveat:** during a broker rebalance all pods hit metadata timeouts at once → all flip unhealthy and restart together, and restart churn feeds consumer-group rebalancing. It is consistent with the existing async-path behavior (no debounce there either), so not a regression — but consider flipping unhealthy only after N consecutive synchronous failures if restart-storms-during-rebalance become a concern.

## Recommendation

**Target B (outbox); ship the synchronous-throw fix immediately and independently.** The outbox is the only option that removes the *trigger* of every recurrence — the client-facing produce timeout — instead of only making the retry safe. If a full outbox is too big for the current sprint, **Option A is a valid stepping stone** that closes the duplicate/poison-pill hole now; since a correct Option A already stores the payload and runs a reconciliation loop, it migrates into B later with little waste.

## Open questions

- Audit: are all `fager.notifikasjon` consumers (BigQuery export, ekstern-varsling, skedulert-*) idempotent on a re-delivered `hendelseId`? (Required for "reuse the id" to be safe — though at-least-once already implies it.)
- nySak: reserve on `(merkelapp, grupperingsid)` and carry both events — confirm the shape.
- Outbox relay: single-threaded vs per-partition; where it lives (own process vs in `produsent`).
- Cleanup of historical duplicates from the four incidents (still outstanding per the May postmortem).
