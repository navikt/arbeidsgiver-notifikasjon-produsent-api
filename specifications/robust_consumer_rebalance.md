# Robust rebalance / commit-failure handling in `CoroutineKafkaConsumer`

**Status:** Design proposal — for team decision, no code written yet
**Author:** drafted with Claude, 2026-06-01 (updated 2026-06-02 with confirming evidence)
**Related:** [postmortem 2026-06-01](../postmortems/duplikater_etter_kafka_outage_2026-06-01.md)

## Background

After the 2026-06-01 Kafka outage *recovered*, the `produsent-model-builder` consumer stayed stuck, repeatedly logging:

```
Offset commit failed on partition fager.notifikasjon-13 at offset 2092410:
The coordinator is not aware of this member.
```

The outage log shows the signature of a reprocessing loop rather than forward progress — the same offset re-attempted ~1s apart:

```
15:42:18.555Z  fager.notifikasjon-18 @2157658
15:42:19.524Z  fager.notifikasjon-18 @2157658
```

`The coordinator is not aware of this member` is `CommitFailedException`: the consumer was removed from the group (a rebalance happened) and is committing offsets for a generation it no longer owns. This is a **control-plane** condition — the consumer needs to rejoin and re-fetch — but the current code treats it as an ordinary **data** error and retries the same record forever, so the group never re-stabilizes on its own.

### Confirmed by the 2026-06-02 logs (`reaper-model-builder`)

The morning after, a *different* group on the **same `CoroutineKafkaConsumer` code** — `reaper-model-builder` (`notifikasjon-kafka-reaper`) — captured the complete chain, on **ordinary records** (`EksterntVarselVellykket`, `BrukerKlikket` — no poison pill involved). It is not merely "stuck"; the consumer **crashed**:

```
08:20:22  TimeoutException: Timeout of 60000ms expired before successfully committing offsets
          {fager.notifikasjon-15=OffsetAndMetadata{offset=2102262...}}          ← commitSync blocked 60s
08:21:00  CommitFailedException: ... the time between subsequent calls to poll() was longer than
          the configured max.poll.interval.ms ...                              ← evicted (self-inflicted)
08:21:05  Exception in thread "DefaultDispatcher-worker-11"
          java.lang.IllegalStateException: No current assignment for partition fager.notifikasjon-1
              at KafkaConsumer.resume(...)
              at CoroutineKafkaConsumer$forEach$2.invokeSuspend(CoroutineKafkaConsumer.kt:123)
              StandaloneCoroutine{Cancelling}                                    ← consumer coroutine dies
```

This tells us three concrete things beyond the original write-up:
- The **trigger** is the unbounded `commitSync` — it blocks up to `default.api.timeout.ms` (60s) and starves the poll loop until it exceeds `max.poll.interval.ms`. Kafka diagnoses this itself in the `CommitFailedException` message.
- The **crash point** is `consumer.resume(resumeQueue.pollAll())` at **`CoroutineKafkaConsumer.kt:123`**, not the `seek`/`pause` in the catch — the `resumeQueue` retained a partition that the rebalance had revoked.
- The failure mode is a **silently dead consumer** (coroutine `Cancelling`, pod stays "alive"), not an infinite retry. Lag then grows with no health signal. This is **generic to every consumer using `CoroutineKafkaConsumer`**, not specific to one group.

## Problem

In `CoroutineKafkaConsumer.forEachRecord` (`CoroutineKafkaConsumer.kt:154-202`), one `try/catch` wraps **both** the record handler and the commit:

```kotlin
try {
    withContext(Dispatchers.IO) { body(record) }                                   // data plane
    consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))// control plane
    retries.set(0)
} catch (e: Exception) {                                                           // line 176 — both end up here
    val attempt = retries.incrementAndGet()
    ...
    consumer.seek(currentPartition, record.offset())   // line 193
    consumer.pause(listOf(currentPartition))           // line 194
    retryTimer.schedule(backoffMillis) { resumeQueue.offer(currentPartition) }
    return@currentPartition
}
```

Four defects follow from this:

0. **The unbounded `commitSync` is the trigger.** `commitSync(...)` (line 171) is called with **no timeout**, so it blocks up to `default.api.timeout.ms` (60s) when the coordinator is unavailable — confirmed by `TimeoutException: Timeout of 60000ms expired before successfully committing offsets`. Done **per record** (up to `max.poll.records=10`), a couple of these 60s blocks push the time between `poll()` calls past `max.poll.interval.ms` (300s), so the broker evicts the member. The eviction is therefore **self-inflicted**, independent of any ongoing cluster problem — which is why it persists after recovery.

1. **No distinction between a poison pill and a lost membership.** Once evicted, the resulting `CommitFailedException` is handled identically to a `body(record)` failure: `seek` back, `pause`, back off, retry the *same offset* forever. After an eviction the correct action is to **abort the in-flight batch and rejoin**, not to re-commit stale-generation offsets.

2. **No rebalance awareness on the normal path.** `init` calls `consumer.subscribe(listOf(topic))` (`CoroutineKafkaConsumer.kt:92`) with **no** `ConsumerRebalanceListener`. The listener (`seekToBeginningOnAssignment`, lines 224-242) is only installed when `seekToBeginning = true`, which `produsent-model-builder` / `reaper-model-builder` do not use. So the loop never learns that partitions were revoked; it keeps iterating a stale `ConsumerRecords` batch fetched under the old generation, and the `resumeQueue` keeps partitions the consumer no longer owns.

3. **Operating on a revoked partition throws and kills the consumer.** With eager rebalancing, a rebalance revokes all partitions before reassigning a subset. The next loop iteration runs `consumer.resume(resumeQueue.pollAll())` at **`CoroutineKafkaConsumer.kt:123`** for a partition queued by an earlier backoff — now unassigned — and the client throws `IllegalStateException: No current assignment for partition …` (confirmed in the 2026-06-02 log). The same hazard exists for the `seek`/`pause` in the catch (lines 193-194). This exception is **not** caught inside `forEach`/`forEachRecord` — it unwinds out and the coroutine ends up `Cancelling`, so **the consumer dies while the pod stays "alive"** (this path never touches `Health`).

The result: an eviction (self-inflicted via the 60s commit block) becomes a reprocess → commit-fails loop, and the stale `resumeQueue` then crashes the consumer outright — a silently dead consumer that does not recover when the cluster does.

## Goals / non-goals

**Goals**
- A commit must never block the poll loop long enough to cause a self-inflicted eviction.
- A rebalance / commit failure must lead to a clean rejoin and re-fetch, not an infinite same-offset retry.
- The consumer must survive partition revocation without throwing out of the poll loop (no `IllegalStateException` crash → no silently dead consumer).
- Per-partition retry state and the `resumeQueue` must not leak across revocation.

**Non-goals (explicitly out of scope)**
- **Poison-pill behavior is unchanged.** A failure in `body(record)` still does seek + pause + exponential backoff + retry forever, capped below `max.poll.interval.ms`. We do *not* want to go unhealthy on a poison pill.
- **No health/liveness changes** here (covered separately; the synchronous-`send()` flip is its own change).
- **No producer idempotency changes** (see [idempotent oppretting](idempotent_oppretting_notifikasjon.md)).
- **Operational pod-restart churn** (liveness flapping → group rebalances) is a deployment concern, not this code change. This fix makes the consumer *recover gracefully* from rebalances; it does not stop an externally-driven rebalance storm.

## Proposed design

### 0. Bound the commit so it can never starve the poll loop (the trigger)

This is the change that stops the self-inflicted eviction. Options, in rough order of preference:

- **Bound `commitSync` with a timeout:** `consumer.commitSync(offsets, Duration.ofSeconds(5))` instead of the no-arg overload, so a struggling commit fails fast (well under `max.poll.interval.ms`) instead of blocking 60s. The fast failure is then handled as a control-plane abort (step 1).
- **Commit once per poll-batch** rather than per record: at most one commit round-trip per `poll()` instead of up to `max.poll.records`, shrinking both the blocking time and the mid-batch-rebalance window.
- **Lower `max.poll.records`** (currently 10) as a cheap secondary safeguard so a single batch can't accumulate many slow commits.

Without this, the eviction loop can reseed itself even with perfect rebalance handling, because the very act of committing is what blows the poll interval.

### 1. Separate the data plane from the control plane

Split the single `try/catch` so the commit has its own handling:

```kotlin
// data plane — unchanged poison-pill semantics
try {
    withContext(Dispatchers.IO) { body(record) }
} catch (e: Exception) {
    retryWithBackoff(record, partition)   // seek + pause + backoff + retry forever (as today)
    return@currentPartition
}

// control plane — new
try {
    if (partition in consumer.assignment()) {
        consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
        retries.set(0)
    } else {
        // partition revoked mid-batch — drop the rest of this stale batch, rejoin on next poll()
        return@forEachRecord
    }
} catch (e: CommitFailedException) {
    // membership lost — abort the batch, let poll() rejoin and re-fetch. Do NOT re-commit stale offsets.
    log.warn("commit failed (membership lost); aborting batch to rejoin", e)
    return@forEachRecord
} catch (e: RebalanceInProgressException) {
    return@forEachRecord
}
```

Key points:
- `return@forEachRecord` aborts the **whole** current batch (not just the partition). Records already handled but not committed will be re-fetched and reprocessed after rejoin — safe because the handlers are idempotent (`on conflict do nothing`).
- `commitSync` is guarded by an `assignment()` check so it is never attempted for a revoked partition.

### 2. Always install a `ConsumerRebalanceListener`

Replace the bare `consumer.subscribe(listOf(topic))` with a subscription that always carries a listener; fold the existing `seekToBeginning` behavior into it:

```kotlin
consumer.subscribe(listOf(topic), object : ConsumerRebalanceListener {
    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
        partitions.forEach { p ->
            retriesPerPartition.remove(p.partition())   // drop stale retry state
            resumeQueue.remove(p)                        // don't resume a partition we no longer own
            onPartitionRevoked?.invoke(p)
        }
    }
    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        if (seekToBeginning) {
            consumer.endOffsets(partitions).forEach { (p, end) -> onPartitionAssigned?.invoke(p, end - 1) }
            consumer.seekToBeginning(partitions)
        }
    }
})
```

This removes the special-case `seekToBeginningOnAssignment()` and gives every consumer (including `produsent-model-builder`) revocation cleanup.

### 3. Guard every partition-scoped call against the current assignment

The 2026-06-02 crash was at `consumer.resume(resumeQueue.pollAll())` (`CoroutineKafkaConsumer.kt:123`) — `resume` on a revoked partition. The same hazard applies to `seek`/`pause`. Filter all three against `consumer.assignment()`:

```kotlin
// in forEach, before polling:
consumer.resume(resumeQueue.pollAll().filter { it in consumer.assignment() })

// in the poison-pill backoff path:
if (currentPartition in consumer.assignment()) {
    consumer.seek(currentPartition, record.offset())
    consumer.pause(listOf(currentPartition))
    retryTimer.schedule(backoffMillis) { resumeQueue.offer(currentPartition) }
}
```

Combined with the `resumeQueue.remove(p)` cleanup in the rebalance listener (step 2), this guarantees no `IllegalStateException` escapes the poll loop, so the consumer can no longer die silently on a rebalance.

## Correctness notes

- **At-least-once is preserved.** Aborting a batch on commit failure means some already-handled records get reprocessed after rejoin. All `oppdaterModellEtter*` handlers are idempotent (`on conflict do nothing`), so reprocessing is safe — this is the same guarantee the seek-back path relies on today.
- **Poison pill still works.** A `body(record)` failure never reaches the control-plane block; it retries forever exactly as now. A poison pill and a rebalance are now handled by different code paths.
- **Eager vs cooperative assignors.** The `assignment()` guards are correct for both; with eager rebalancing all partitions are briefly revoked, which is exactly the case the guards protect against.
- **Retry-state leak fixed.** `retriesPerPartition` is keyed by partition number and previously survived revocation; the listener now clears it on revoke so a reassigned partition starts clean.

## Testing

- Unit/integration with the existing `LocalKafka` test util:
  - Simulate a rebalance/eviction between `poll()` and `commitSync` → assert the batch is aborted and reprocessed after rejoin, no infinite same-offset loop, consumer stays in the loop (does not throw out).
  - **Revoke a partition that is queued in `resumeQueue` (or currently paused/in-backoff), then poll** → assert no `IllegalStateException` escapes and the consumer keeps running (regression test for the `CoroutineKafkaConsumer.kt:123` crash). Assert retry state and the queue entry are cleared.
  - A slow/failing commit → assert the bounded `commitSync` fails fast and aborts the batch rather than blocking ~60s.
  - Poison-pill record → assert unchanged: retries forever with capped backoff, never marks unhealthy.

## Rollout / risk

- Behavioral change is confined to the commit/rebalance path; the poison-pill and happy paths are unchanged.
- Main risk is the refactor of `forEachRecord` control flow — cover with the tests above before deploy.
- Independent of the idempotency work and the health-flip change; can ship on its own.

## Open questions

- For step 0: bounded `commitSync` timeout (e.g. 5s) vs. per-batch commit vs. lower `max.poll.records` — which combination? Bounded timeout is the minimum; per-batch is the bigger but cleaner change.
- Should a sustained inability to commit/rejoin (minutes) — or a **dead consumer coroutine** — emit a **metric/alert** (not a liveness restart) so a silently-stopped consumer is visible? (Liveness restart is the wrong tool — it would also fire during a legitimate broker outage. A "consumer loop exited unexpectedly" signal is arguably the one case that *should* flip health, since the pod genuinely can't recover on its own.)
- Confirm the deployed assignor (`partition.assignment.strategy` is unset → client default) so the `assignment()` guards match production behavior.
