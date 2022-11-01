package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.consumer.Consumer
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

class PeriodicReplayer<K, V>(
    private val consumer: Consumer<K, V>,
    private val isBigLeap: Predicate<LocalDateTime>,
    private val isSmallLeap: Predicate<LocalDateTime>,
    private val bigLeap: Long,
    private val smallLeap: Long,
    private val enabled: Boolean,
) {
    private val log = logger()
    private val lastTick = AtomicReference<LocalDateTime>()

    fun replayWhenLeap() {
        if (!enabled) {
            return
        }

        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        if (now == lastTick.getAndSet(now)) {
            // noop
        } else if (isBigLeap.test(now)) {
            log.info("replaying big leap")
            consumer.replay(bigLeap)

        } else if (isSmallLeap.test(now)) {
            log.info("replaying small leap")
            consumer.replay(smallLeap)
        }
    }
}

/**
 * replays the number of records for all partitions that are not paused
 * by seeking to the current offset - number of records
 */
private fun <K, V> Consumer<K, V>.replay(numberOfRecords: Long) {
    (assignment() - paused()).forEach {
        val currentPosition = position(it)
        val newPosition = (currentPosition - numberOfRecords).coerceAtLeast(0)

        seek(it, newPosition)
    }
}