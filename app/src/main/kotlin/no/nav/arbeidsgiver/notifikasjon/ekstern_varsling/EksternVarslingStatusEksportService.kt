package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.*
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDateTime
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class EksternVarslingStatusEksportService(
    private val åpningstider: Åpningstider = ÅpningstiderImpl,
    val eventSource: HendelsesstrømKafkaImpl,
    val repo: EksternVarslingRepository,
    val kafka: KafkaProducer<String, VarslingStatusDto> = createKafkaProducer(),
) {

    fun start(coroutineScope: CoroutineScope): Job {
        return coroutineScope.launch {
            eventSource.forEach { event, meta ->
                prosesserHendelse(event, meta)
            }
        }
    }

    suspend fun prosesserHendelse(
        event: HendelseModel.Hendelse,
        meta: HendelseModel.HendelseMetadata
    ) {
        val varslingStatus = when (event) {
            is HendelseModel.EksterntVarselFeilet -> {
                val status = when (event.altinnFeilkode) {
                    "30304",
                    "30307",
                    "30308" -> Status.MANGLER_KOFUVI

                    else -> Status.ANNEN_FEIL
                }

                lagVarslingStatus(
                    status = status,
                    virksomhetsnummer = event.virksomhetsnummer,
                    varselId = event.varselId,
                    hendelseTimestamp = meta.timestamp
                ) ?: return
            }

            is HendelseModel.EksterntVarselVellykket -> {
                lagVarslingStatus(
                    status = Status.OK,
                    virksomhetsnummer = event.virksomhetsnummer,
                    varselId = event.varselId,
                    hendelseTimestamp = meta.timestamp
                ) ?: return
            }

            else -> return
        }

        kafka.suspendingSend(ProducerRecord(TOPIC, varslingStatus))
    }

    private suspend fun lagVarslingStatus(
        status: Status,
        virksomhetsnummer: String,
        varselId: UUID,
        hendelseTimestamp: Instant
    ) = repo.findVarsel(varselId)?.let { varsel ->
        VarslingStatusDto(
            virksomhetsnummer = virksomhetsnummer,
            varselId = varselId,
            varselTimestamp = varsel.data.eksternVarsel.sendeTidspunkt
                ?: varsel.kalkuertSendetidspunkt(åpningstider, hendelseTimestamp.asOsloLocalDateTime()),
            kvittertEventTimestamp = hendelseTimestamp,
            status = status
        )
    }
}


const val TOPIC = "fager.ekstern-varsling-status"

private fun createKafkaProducer() = KafkaProducer<String, VarslingStatusDto>(
    COMMON_PROPERTIES + SSL_PROPERTIES + mapOf<String, Any>(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.canonicalName,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to VarslingStatusDtoSerializer::class.java.canonicalName,
        ProducerConfig.MAX_BLOCK_MS_CONFIG to 5_000,
        ProducerConfig.ACKS_CONFIG to "all",
    )
)

data class VarslingStatusDto(
    val virksomhetsnummer: String,
    val varselId: UUID,
    val varselTimestamp: LocalDateTime,
    val kvittertEventTimestamp: Instant,
    val status: Status,
    val version: String = "1",
)

enum class Status {
    OK,
    MANGLER_KOFUVI,
    ANNEN_FEIL,
}

class VarslingStatusDtoSerializer : JsonSerializer<VarslingStatusDto>