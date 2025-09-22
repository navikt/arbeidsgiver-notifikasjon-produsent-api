package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.Hendelsesstrøm
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class HendelsesstrømKafkaImpl(
    topic: String = NOTIFIKASJON_TOPIC,
    groupId: String,
    seekToBeginning: Boolean = false,
    replayPeriodically: Boolean = false,
    configure: Properties.() -> Unit = {},
): Hendelsesstrøm {

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

        // soft delete rett etter opprettelse gjør at partisjon er stuck i prod.
        // se også https://github.com/navikt/arbeidsgiver-notifikasjon-produsent-api/pull/800
        UUID.fromString("34a375aa-d967-4327-911b-177afcce9d6e"),
    )

    override suspend fun forEach(
        stop: AtomicBoolean,
        onTombstone: suspend (UUID) -> Unit,
        body: suspend (Hendelse, HendelseMetadata) -> Unit
    ) {
        consumer.forEach(stop) { record ->
            val recordValue = record.value()
            if (recordValue == null) {
                onTombstone(UUID.fromString(record.key()))
            } else if (recordValue.hendelseId in brokenHendelseId) {
                /* do nothing */
            } else {
                withContext(recordValue.asMdcContext()) {
                    body(recordValue, HendelseMetadata.fromKafkaTimestamp(record.timestamp()))
                }
            }
        }
    }

    fun wakeup() = consumer.wakeup()
    fun close() = consumer.close()
}


private fun Hendelse.asMdcContext() = MDCContext(
    mapOf(
        "aggregateId" to aggregateId.toString(),
        "produsentId" to (produsentId ?: ""),
        "hendelseId" to hendelseId.toString(),
    )
)



