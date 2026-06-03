# Robust commit-failure handling in `CoroutineKafkaConsumer`

**Status:** Design proposal — for team decision, no code written yet
**Author:** drafted with Claude, 2026-06-01 (simplified 2026-06-02)
**Related:** [postmortem 2026-06-01](../postmortems/duplikater_etter_kafka_outage_2026-06-01.md)

## Background

After the 2026-06-01 outage recovered, consumers on the shared `CoroutineKafkaConsumer` (`produsent-model-builder`, and on 2026-06-02 `reaper-model-builder`) stayed stuck and ultimately **crashed silently**. The chain, from the 2026-06-02 log:

```
1. commitSync (no timeout) blocks 60 s → TimeoutException: Timeout of 60000ms expired ...
2. those 60 s blocks starve the poll loop → max.poll.interval.ms exceeded → member evicted
   → CommitFailedException: ... time between poll() calls longer than max.poll.interval.ms
3. consumer.resume(resumeQueue.pollAll()) on a now-revoked partition (CoroutineKafkaConsumer.kt:123)
   → IllegalStateException: No current assignment for partition ... → coroutine dies, pod stays "alive"
```

The whole chain is **self-inflicted**: the unbounded `commitSync` is the trigger, and the loop treats the resulting commit failure as an ordinary data error (`seek`/`pause`/retry), which both reseeds the loop and leaves stale entries in `resumeQueue` that later crash it.

## The simplification

Step 1 is the trigger and step 3 is the crash; both stem from treating a Kafka **commit/membership** problem as a **data** problem.

If a commit failure instead flips `Health.subsystemAlive[KAFKA] = false`, the loop exits (its `while` already checks `!Health.terminating`) and the pod restarts — and **the restart is the clean rejoin**. That removes the need for in-process rebalance recovery (no data/control-plane split, no `ConsumerRebalanceListener`, no `resumeQueue` reconciliation). What remains is a tiny guard so a partition revoked by an *unrelated* rebalance can't throw `IllegalStateException` out of the loop.

## Goals / non-goals

**Goals**
- A commit can't block the poll loop long enough to self-evict.
- A commit/membership failure leads to a clean restart (visible), not a silent dead coroutine.
- A revoked partition never throws out of the poll loop.

**Non-goals**
- **Poison-pill behavior is unchanged.** A `body(record)` failure still does seek + pause + capped exponential backoff + retry forever. We do *not* go unhealthy on a poison pill.
- No producer idempotency changes (see [idempotent oppretting](idempotent_oppretting_notifikasjon.md)); the consumer-side 0-rows-skip (`d1a8609c`) is independent.

## Design — three small changes

### 1. Bound the commit
`commitSync(...)` (`CoroutineKafkaConsumer.kt:171`) is called with no timeout and blocks up to `default.api.timeout.ms` (60 s). Pass a short timeout so it fails fast instead of starving the poll loop:

```kotlin
consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)), Duration.ofSeconds(5))
```

### 2. Commit failure → signal unhealthy (don't seek/pause/retry)
Separate the commit from the record handler so a commit failure is handled as a Kafka problem, not a data error:

```kotlin
try {
    withContext(Dispatchers.IO) { body(record) }      // data plane — unchanged poison-pill path on failure
} catch (e: Exception) {
    retryWithBackoff(record, partition)               // seek + pause + backoff + retry forever (as today)
    return@currentPartition
}
try {
    consumer.commitSync(offsets, Duration.ofSeconds(5))
    retries.set(0)
} catch (e: Exception) {                              // TimeoutException, CommitFailedException, ...
    log.error("commit failed; signalling unhealthy to force a clean rejoin on restart", e)
    Health.subsystemAlive[Subsystem.KAFKA] = false    // → while(!Health.terminating) exits → consumer closes → pod restarts
    return
}
```

Setting the flag both **stops the loop** (`Health.terminating` becomes true) and **fails the liveness probe** so NAIS restarts the pod. The in-flight record (handled, not committed) is simply reprocessed after the restart — at-least-once, which the idempotent handlers already tolerate. No batch-abort logic, no rejoin bookkeeping.

### 3. Guard partition-scoped calls against the current assignment
The observed crash was `consumer.resume(resumeQueue.pollAll())` (line 123) on a partition an unrelated rebalance had revoked. `resumeQueue` is populated by the poison-pill backoff path, so this can fire on any rebalance (e.g. a deploy) that lands while a partition is paused. Filter against the live assignment:

```kotlin
// before polling:
consumer.resume(resumeQueue.pollAll().filter { it in consumer.assignment() })

// in the poison-pill backoff path, only act on partitions we still own:
if (currentPartition in consumer.assignment()) {
    consumer.seek(currentPartition, record.offset())
    consumer.pause(listOf(currentPartition))
    retryTimer.schedule(backoffMillis) { resumeQueue.offer(currentPartition) }
}
```

This is the one piece of rebalance-awareness still needed once restart handles the rejoin.

## Why this avoids the cascade

- **TimeoutException (step 1):** bounded commit fails in ~5 s instead of 60 s, and #2 turns it into a restart before the 60 s blocks can accumulate past `max.poll.interval.ms`.
- **CommitFailedException (step 2):** the member is never self-evicted (poll loop no longer starved); if a commit still fails for any reason, #2 restarts cleanly instead of retrying a stale-generation offset.
- **IllegalStateException (step 3):** commit failures no longer feed `resumeQueue` (they restart), and the #3 guard stops a `body()`-error-paused partition from crashing on an unrelated rebalance.

So your hypothesis holds: handling the commit failure (signal unhealthy) removes both the `CommitFailedException` loop and the `IllegalStateException` crash — with the small `assignment()` guard as the only residual rebalance-awareness.

## Testing

- Slow/failing commit → bounded `commitSync` fails fast, `KAFKA=false` set, loop exits (regression test that the consumer does not silently hang or spin).
- Partition queued in `resumeQueue` then revoked, then poll → no `IllegalStateException` escapes (regression for the `kt:123` crash).
- Poison-pill `body()` record → unchanged: retries forever with capped backoff, `KAFKA` stays true.

## Rollout / risk

- Confined to the commit path + two `assignment()` guards; poison-pill and happy paths unchanged. Much smaller than the original draft (no rebalance listener, no batch-abort control flow).
- **Any commit failure restarts the pod.** This includes a benign rebalance (deploy/scale) where a partition is revoked just as we try to commit it — that pod will restart and rejoin. It's rare, cheap, and self-healing (restart = clean rejoin), so we accept it rather than adding logic to distinguish benign revokes from real failures. If it proves noisy, the consecutive-failure threshold (open question) damps it.
- **Restart-storm caveat:** during a genuine broker outage, commits fail across pods → all go unhealthy → restart loop until the broker recovers. Same trade-off accepted for the producer synchronous-`send()` flip: noisy and visible (alerts) beats silent death, and it self-recovers.

## Open questions

- Should `commitSync` failure flip `KAFKA=false` directly, or go through a small consecutive-failure threshold to damp restart storms during a broker outage? (Same question as the producer flip.)
- Confirm the deployed `partition.assignment.strategy` (unset → client default) so the `assignment()` guards match production.
