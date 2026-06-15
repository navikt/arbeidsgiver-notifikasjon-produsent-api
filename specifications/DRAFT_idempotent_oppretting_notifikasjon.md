# Transactional outbox + idempotency for all produsent-api mutations

**Status:** Design proposal — for team decision, no code written yet
**Author:** drafted with Claude, 2026-06-01 (rewritten 2026-06-02: outbox-only, all mutations)
**Related:** [postmortem 2026-06-01](../postmortems/duplikater_etter_kafka_outage_2026-06-01.md), [robust rebalansering](robust_consumer_rebalance.md)

## Goal

1. **Every event-producing mutation goes through a transactional outbox** — the GraphQL request never produces to Kafka synchronously.
2. **Every mutation is idempotent** — a client retry (or any duplicate request) for the same logical operation never produces a second event.

This supersedes the earlier create-only framing (`nyOppgave`/`nyBeskjed`/`nySak`). The reservation alternative is dropped; the outbox is the chosen direction.

## Why (recap)

Today `HendelseDispatcher.send` produces to Kafka first, then writes the read-model ([HendelseDispatcher.kt:12-20](../app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/HendelseDispatcher.kt)):

```kotlin
internal suspend fun send(vararg hendelser: Hendelse) {
    val metadatas = hendelser.map { kafkaProducer.sendOgHentMetadata(it) }   // Kafka first (can time out / hang)
    hendelser.zip(metadatas).forEach { (h, m) -> produsentRepository.oppdaterModellEtterHendelse(h, m) }  // DB only after
}
```

Two structural problems fall out of this:

- **Dual write, wrong order.** A produce timeout throws before the read-model is written, so the mutation's idempotency check (`hentNotifikasjon`, `notifikasjonOppdateringFinnes`, …) is **blind** to in-flight events. A retry then mints a new random `hendelseId` and creates a duplicate. (This is the root cause of the May and June duplicate incidents.)
- **Kafka is on the request path.** A slow broker hangs the mutation up to `delivery.timeout.ms` (the observed 56 s span), and surfaces timeouts that *cause* the client retries that create duplicates.

Idempotency today is also **partial and inconsistent**: creates dedupe on the `(merkelapp, eksternId)` coordinate; some updates accept an *optional* `idempotencyKey`; several updates have no idempotency at all. The goal makes it uniform.

## Constraints / non-goals

- **`hendelseId` / `notifikasjonId` generation is unchanged** — still `UUID.randomUUID()`. The outbox *stores* the generated id and the relay reuses it; the identity scheme is not touched.
- **Consumer-side fixes stay as-is** — the 0-rows-skip (commit `d1a8609c`) and the [rebalance/commit fix](robust_consumer_rebalance.md) are independent and complementary.
- **No `unikt_koordinat` relaxation** — that option was rejected; the constraint stays.
- This spec covers the produsent-api **write path** only.

## Core design

### 1. One choke point: implement the outbox in `HendelseDispatcher`

All ~17 event-producing call sites go through `hendelseDispatcher.send(...)` (every mutation: `nyOppgave`, `nyBeskjed`, `nyKalenderavtale`, `nySak`, `nyStatusSak`, `nesteStegSak`, `tilleggsinformasjonSak`, `oppgaveUtfoert`, `oppgaveUtgaatt`, `oppgaveUtsettFrist`, `oppgaveEndrePaaminnelse`, `oppdaterKalenderavtale`, `softDelete*`, `hardDelete*`, plus the `*ByEksternId`/`*ByGrupperingsid`/`_V2` variants that resolve an aggregate and then call the same `send`). Reworking `send` therefore upgrades **all** mutations at once.

New `send` — a single DB transaction, no Kafka:

```kotlin
internal suspend fun send(idempotencyKey: IdempotencyKey, vararg hendelser: Hendelse): SendResult =
    produsentRepository.transaction {
        // 1. claim the operation; if already claimed, this is a retry → return the prior result
        if (!claimIdempotencyKey(idempotencyKey)) return@transaction SendResult.AlreadyApplied(idempotencyKey)
        // 2. enqueue the event(s) to the outbox (relay → Kafka), preserving order
        hendelser.forEach { enqueueOutbox(it) }
        // 3. update the read-model in the SAME tx (read-your-writes; consumer re-applies idempotently later)
        hendelser.forEach { oppdaterModellEtterHendelse(it, mottattMetadata()) }
        SendResult.Applied
    }
```

The mutation no longer needs its own pre-produce `hentNotifikasjon` race check: the idempotency claim **is** the dedup, and it is atomic with the enqueue and the read-model write.

### 2. Why this makes idempotency reliable

The blind window existed because the read-model was only written *after* a Kafka round-trip. Here the **idempotency claim, the outbox enqueue, and the read-model update commit together in one local transaction**, with no Kafka in between. So a retry's claim always sees the prior claim (read-your-writes within Postgres), regardless of broker state or consumer lag. The duplicate can no longer be created.

### 3. The relay

A background loop (reuse `launchProcessingLoop`) drains the outbox to Kafka with the existing producer + `OrgnrPartitioner`:

```
select * from hendelse_outbox where status = 'UNSENT' order by id   -- per-partition ordering, see below
  → producer.send(payload, key = hendelseId)   -- reuse stored hendelseId; at-least-once
  → mark SENT
```

- **At-least-once to Kafka.** A crash between produce and `mark SENT` re-produces the same `hendelseId`; the model-builder dedupes on PK (and the 0-rows-skip handles coordinate dupes). Consumers already tolerate redelivery.
- **Ordering.** Events must reach Kafka in per-`virksomhetsnummer` (per-partition) order. The outbox `id` is monotonic; a single-threaded relay reading in `id` order is trivially correct. For throughput, shard by target partition / `virksomhetsnummer` and keep per-shard order. With multiple produsent pods, use `select … for update skip locked` partitioned by orgnr (or a single active relay via advisory lock) so two pods never reorder the same key.
- **Kafka outage resilience.** Mutations keep succeeding against Postgres during a broker outage; the outbox backs up and the relay drains on recovery. **No produce timeouts on the request path, so no client retries, so no duplicates** — this is the property the reservation alternative lacked.

## Idempotency key per mutation

Every mutation resolves to a stable `IdempotencyKey` that is identical across retries of the same logical operation. Three buckets:

| Bucket | Mutations | Key |
|---|---|---|
| **A. Coordinate creates** | `nyOppgave`, `nyBeskjed`, `nyKalenderavtale`, `nySak` | `(merkelapp, eksternId)` — or `(merkelapp, grupperingsid)` for sak. Already the natural key; backed by `unikt_koordinat` / `grupperingsid_unique`. |
| **B. Producer-supplied key** | `nyStatusSak`, `nesteStegSak`, `tilleggsinformasjonSak`, `oppgaveEndrePaaminnelse`, `oppgaveUtsettFrist`, `oppdaterKalenderavtale` | The producer's `idempotencyKey` (already accepted by several of these, scoped to the aggregate). **Make it consistent across all repeatable updates**; today it is optional/missing on some. |
| **C. Finalizing / delete** | `oppgaveUtfoert`, `oppgaveUtgaatt`, `softDelete*`, `hardDelete*` | Derived `(aggregateId, operation)` — at-most-once per aggregate+operation; naturally idempotent end-state. |

Notes:
- Bucket B is where the gap is widest today. `nyStatusSak` already enforces `idempotence_key_unique (sak_id, idempotence_key)` (V11:33); `nesteStegSak`/`tilleggsinformasjonSak`/`oppgaveEndrePaaminnelse` accept an **optional** `idempotencyKey` and dedupe via `notifikasjon_oppdatering` (V24) only when present. The proposal is to require/derive a key for all of B so a retry is always safe. For value-changing updates with no producer key, fall back to a content-derived key (a retry with identical payload dedupes; a genuinely new value does not).
- The outbox idempotency claim subsumes these per-aggregate checks at enqueue time, so the consumer-side `notifikasjon_oppdatering` / sak-idempotence tables become a redundant backstop rather than the primary guard.

## Schema

```sql
-- V3x__hendelse_outbox.sql
create table hendelse_outbox (
    id            bigserial primary key,         -- relay order
    hendelse_id   uuid not null,                 -- the (unchanged) random id; relay's Kafka key
    virksomhetsnummer text,                       -- for per-partition ordering / sharding
    hendelse      jsonb not null,
    status        text not null default 'UNSENT',-- 'UNSENT' | 'SENT'
    opprettet     timestamptz not null default now()
);

-- V3x__mutasjon_idempotens.sql
create table mutasjon_idempotens (
    idempotency_key text primary key,            -- e.g. 'OPPGAVE:<merkelapp>:<eksternId>' / 'STATUS:<sakId>:<key>' / 'UTFOERT:<aggregateId>'
    hendelse_id     uuid not null,               -- the id assigned to the first attempt
    opprettet       timestamptz not null default now()
);
```

The claim is `insert into mutasjon_idempotens(...) on conflict do nothing` → 1 row = claimed (proceed), 0 rows = retry (return prior result). For creates this is equivalent to today's coordinate check but reliable; for updates it makes idempotency uniform.

## Correctness

- **Dual-write eliminated at the request boundary.** The mutation commits one local transaction; Kafka is reached only by the relay. The only residual ambiguity (relay produced but crashed before `mark SENT`) resolves to a harmless re-produce of the same `hendelseId`.
- **Read-your-writes preserved.** The read-model is updated in the same tx, so the GraphQL response and immediate follow-up queries are consistent even though Kafka visibility is asynchronous.
- **At-least-once, ordered.** Per-partition order preserved via outbox `id`; redelivery tolerated by idempotent consumers.
- **No new poison pill.** The relay reuses the stored `hendelseId`, so the consumer dedupes redeliveries on PK.

## Migration / rollout

1. Add `hendelse_outbox` + `mutasjon_idempotens` tables.
2. Rework `HendelseDispatcher.send` to {claim → enqueue → update read-model} in one tx; thread an `IdempotencyKey` from each mutation (buckets A/B/C). Mutations stop calling the producer directly.
3. Add the relay loop; reuse the existing producer/partitioner.
4. Roll out behind the scenes — the GraphQL contract is unchanged except that previously-optional `idempotencyKey` inputs (bucket B) become recommended/required; coordinate-keyed creates (A) and finalizing ops (C) need no producer change.
5. The `*ByEksternId` / `*ByGrupperingsid` / `_V2` variants resolve their aggregate first, then call the same reworked `send` — no per-variant work.

## Open questions

- **Relay topology:** single active relay (advisory lock / leader) vs. per-partition workers with `for update skip locked`. Throughput vs. simplicity.
- **Bucket B keys:** require `idempotencyKey` on all repeatable updates, or derive a content-hash default when absent? (Required is cleaner but a producer-facing contract change.)
- **Outbox retention:** prune `SENT` rows on a schedule, or keep for audit?
- **`mottattTidspunkt` semantics:** today it comes from the Kafka record timestamp on consume; with the relay producing later, decide whether the event's logical timestamp is set at enqueue time (recommended) so it is stable regardless of relay delay.
- Relationship to the consumer-side `notifikasjon_oppdatering` idempotence tables once the outbox claim is authoritative — keep as backstop or retire.
