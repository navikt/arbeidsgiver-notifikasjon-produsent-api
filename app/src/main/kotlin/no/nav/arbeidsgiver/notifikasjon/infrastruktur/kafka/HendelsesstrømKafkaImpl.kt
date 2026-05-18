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
    configOverrides: Map<String, Any> = emptyMap(),
): Hendelsesstrøm {

    private val consumer = CoroutineKafkaConsumer.new(
        topic = topic,
        groupId = groupId,
        keyDeserializer = StringDeserializer::class.java,
        valueDeserializer = ValueDeserializer::class.java,
        seekToBeginning = seekToBeginning,
        replayPeriodically = replayPeriodically,
        configOverrides = configOverrides,
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

        UUID.fromString("20d2bbb3-a6b8-4c24-9753-d0e41b7a1d38"), // duplikat som følge av kafka outage 15.5.2026-17.5.2026
        UUID.fromString("93f96dbf-7af6-40d7-b46d-1de67a50831a"), // duplikat som følge av kafka outage 15.5.2026-17.5.2026
        UUID.fromString("0a2b4ee0-84cf-4d2a-9fe3-d2227d08b194"), // duplikat som følge av kafka outage 15.5.2026-17.5.2026

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
}


private fun Hendelse.asMdcContext() = MDCContext(
    mapOf(
        "aggregateId" to aggregateId.toString(),
        "produsentId" to (produsentId ?: ""),
        "hendelseId" to hendelseId.toString(),
    )
)



