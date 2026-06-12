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
- No producer idempotency changes (see [idempotent oppretting](DRAFT_idempotent_oppretting_notifikasjon.md)); the consumer-side 0-rows-skip (`d1a8609c`) is independent.

## Design — three small changes

### 1. Bound the commit
`commitSync(...)` (`CoroutineKafkaConsumer.kt:171`) is called with no timeout and blocks up to `default.api.timeout.ms` (60 s). Pass an explicit timeout so it fails fast instead of starving the poll loop:

```kotlin
consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)), commitTimeout)
```

**Choosing `commitTimeout`.** It is bounded by three considerations, not a single magic number:
- **Upper (hard):** we commit *per record*, up to `max.poll.records = 10` per batch, and the gap between `poll()` calls must stay under `max.poll.interval.ms = 300 s`. So `10 × commitTimeout` must sit well under 300 s → a per-commit budget of ~30 s. The unbounded 60 s default blew this (≈5 commits = eviction); anything ≤ ~10 s has comfortable margin.
- **Lower:** a healthy commit is a single coordinator round-trip (milliseconds). The timeout only needs to clear transient blips (leader election, broker GC), so a few seconds suffices.
- **vs `request.timeout.ms` (30 s default):** a `commitTimeout` shorter than this abandons the commit before the underlying RPC fully retries. That's fine for our "fail fast → restart" goal, but it means we *will* restart on a 5–30 s coordinator slowness that might have resolved.

**Recommendation:** ~**10 s**, paired with the consecutive-failure threshold (#2 / open questions) so a *single* transient slow commit does not restart the pod, but several in a row do. The threshold decouples "how long per attempt" (this timeout) from "how long before we give up" (threshold × timeout), which is the behaviour we actually want — and it is the same knob that damps the restart-storm / benign-rebalance concern.

**Deeper lever:** the `× 10` only bites because we commit per record. Committing **once per poll-batch** (one commit per `poll()`) removes the multiplier and relaxes the upper bound entirely. We left it out for simplicity, but it is the clean way to make `commitTimeout` a non-issue.

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
    consumer.commitSync(offsets, commitTimeout)       // see #1 for commitTimeout
    commitFailures = 0
    retries.set(0)
} catch (e: Exception) {                              // TimeoutException, CommitFailedException, ...
    log.error("commit failed (attempt ${++commitFailures})", e)
    if (commitFailures >= COMMIT_FAILURE_THRESHOLD) { // damp transient blips / benign rebalances
        Health.subsystemAlive[Subsystem.KAFKA] = false // → while(!Health.terminating) exits → consumer closes → pod restarts
    }
    return
}
```

A commit failure short of the threshold just `return`s (re-poll → rejoin on the next loop). Once the threshold is crossed, setting the flag both **stops the loop** (`Health.terminating` becomes true) and **fails the liveness probe** so NAIS restarts the pod. The in-flight record (handled, not committed) is simply reprocessed after the restart — at-least-once, which the idempotent handlers already tolerate. No batch-abort logic, no rejoin bookkeeping. (Threshold = 1 reduces to "restart on first commit failure"; see open questions.)

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

- **TimeoutException (step 1):** the bounded commit fails in `commitTimeout` (~10 s) instead of 60 s, and #2 turns sustained failure into a restart before the blocks can accumulate past `max.poll.interval.ms`.
- **CommitFailedException (step 2):** the member is never self-evicted (poll loop no longer starved); if a commit still fails for any reason, #2 restarts cleanly instead of retrying a stale-generation offset.
- **IllegalStateException (step 3):** commit failures no longer feed `resumeQueue` (they restart), and the #3 guard stops a `body()`-error-paused partition from crashing on an unrelated rebalance.

So your hypothesis holds: handling the commit failure (signal unhealthy) removes both the `CommitFailedException` loop and the `IllegalStateException` crash — with the small `assignment()` guard as the only residual rebalance-awareness.

## Testing

- Slow/failing commit → bounded `commitSync` fails fast, `KAFKA=false` set, loop exits (regression test that the consumer does not silently hang or spin).
- Partition queued in `resumeQueue` then revoked, then poll → no `IllegalStateException` escapes (regression for the `kt:123` crash).
- Poison-pill `body()` record → unchanged: retries forever with capped backoff, `KAFKA` stays true.

## Rollout / risk

- Confined to the commit path + two `assignment()` guards; poison-pill and happy paths unchanged. Much smaller than the original draft (no rebalance listener, no batch-abort control flow).
- **Sustained commit failure (≥ threshold) restarts the pod.** A one-off failure — e.g. a benign rebalance (deploy/scale) revoking a partition just as we commit it — just re-polls and rejoins, no restart. Only repeated failures restart, which is rare, cheap, and self-healing (restart = clean rejoin). The threshold is what keeps benign revokes from causing churn.
- **Restart-storm caveat:** during a genuine broker outage, commits keep failing past the threshold across pods → all go unhealthy → restart loop until the broker recovers. Same trade-off accepted for the producer synchronous-`send()` flip: noisy and visible (alerts) beats silent death, and it self-recovers.

## Open questions

- **`commitTimeout` and `COMMIT_FAILURE_THRESHOLD` values**, and the reset semantics of the failure counter (reset on any successful commit? per-partition vs. global?). Recommendation is ~10 s timeout × a small threshold (e.g. 3); tune against observed commit latency.
- Whether to also commit **once per poll-batch** (relaxes the `× max.poll.records` starvation bound and makes `commitTimeout` far less sensitive) — deferred for simplicity.
- Confirm the deployed `partition.assignment.strategy` (unset → client default) so the `assignment()` guards match production.
