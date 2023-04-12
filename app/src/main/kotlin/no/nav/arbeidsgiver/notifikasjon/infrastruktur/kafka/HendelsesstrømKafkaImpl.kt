package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.Hendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class HendelsesstrømKafkaImpl(
    topic: String = NOTIFIKASJON_TOPIC,
    groupId: String,
    seekToBeginning: Boolean = false,
    replayPeriodically: Boolean = false,
    configure: Properties.() -> Unit = {},
): Hendelsesstrøm {
    private val log = logger()

    private val consumer = CoroutineKafkaConsumer.new(
        topic = topic,
        groupId = groupId,
        keyDeserializer = StringDeserializer::class.java,
        valueDeserializer = ValueDeserializer::class.java,
        seekToBeginning = seekToBeginning,
        replayPeriodically = replayPeriodically,
        configure = configure,
    )


    /**
     * 7.Sep 2022 oppstod en feil med idempotens på sak,
     * der vi endte med å opprette en duplikat sakstatus hvor saksid peker på feil sak som ikke finnes.
     * Ble diskutert i den gamle alerts kanalen: https://nav-it.slack.com/archives/G01KA7H11C5/p1662538289557259
     */
    private val brokenHendelseId: Set<UUID> = setOf(
        UUID.fromString("75977ac3-5ccd-42d2-ada0-93482462b8a9"),
    )

    override suspend fun forEach(
        stop: AtomicBoolean,
        body: suspend (Hendelse, HendelseMetadata) -> Unit
    ) {
        consumer.forEach(stop) { record ->
            val recordValue = record.value()
            if (recordValue == null) {
                log.info("skipping tombstoned event key=${record.key()}")
            } else if (recordValue.hendelseId in brokenHendelseId) {
                /* do nothing */
            } else {
                withContext(recordValue.asMdcContext()) {
                    body(recordValue, HendelseMetadata(Instant.ofEpochMilli(record.timestamp())))
                }
            }
        }
    }
}


private fun Hendelse.asMdcContext() = MDCContext(
    mapOf(
        "aggregateId" to aggregateId.toString(),
        "produsentId" to (produsentId ?: ""),
        "hendelseId" to hendelseId.toString(),
    )
)



