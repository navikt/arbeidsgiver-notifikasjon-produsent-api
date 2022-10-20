package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.consumer.Consumer
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.function.Predicate
import kotlin.concurrent.scheduleAtFixedRate

class PeriodicReplayer<K, V>(
    private val consumer: Consumer<K, V>,
    private val isBigLeap: Predicate<LocalDateTime>,
    private val isSmallLeap: Predicate<LocalDateTime>,
    private val bigLeap: Long,
    private val smallLeap: Long,
) {
    private val log = logger()

    fun start() {
        val start = Date()
        val period = Duration.ofMinutes(1).toMillis()

        Timer("PeriodicReplayer", false).scheduleAtFixedRate(time = start, period = period) {
            val now = LocalDateTime.now()

            if (isBigLeap.test(now)) {
                log.info("replaying big leap")
                consumer.replay(bigLeap)

            } else if (isSmallLeap.test(now)) {
                log.info("replaying small leap")
                consumer.replay(smallLeap)

            } else {
                // nothing to do
                log.info("idling")
            }
        }
    }
}

fun <K, V> Consumer<K, V>.replay(numberOfRecords: Long) {
    assignment().forEach {
        val currentPosition = position(it)
        val newPosition = (currentPosition - numberOfRecords).coerceAtLeast(0)
        seek(it, newPosition)
    }
}