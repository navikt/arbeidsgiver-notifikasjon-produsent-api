package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu.LØPENDE
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu.SPESIFISERT
import no.nav.arbeidsgiver.notifikasjon.hendelse.Hendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class EksternVarslingStatusEksportServiceTest {
    val kafka = MockProducer(true, StringSerializer(), VarslingStatusDtoSerializer())
    private val hendelseMetadata = HendelseModel.HendelseMetadata(Instant.parse("2020-01-01T01:01:01.00Z"))


    @Test
    fun `når hendelse er EksterntVarselFeilet med feilkode`() = runTest {
        listOf(
            "30304",
            "30307",
            "30308",
        ).forEach { feilkode ->
            withTestDatabase(EksternVarsling.databaseConfig) { database ->
                val service = setupService(database, kafka)
                val varselTilstand = varselTilstand(uuid("314"), LØPENDE)
                database.insertVarselTilstand(varselTilstand)

                kafka.clear()
                val event = eksterntVarselFeilet(feilkode, uuid("314"))
                service.testProsesserHendelse(event, hendelseMetadata)

                // sender en VarslingStatusDto med status MANGLER_KOFUVI
                assertEquals(1, kafka.history().size)
                kafka.history().first().value().let {
                    assertEquals(Status.MANGLER_KOFUVI, it.status)

                    assertEquals(event.virksomhetsnummer, it.virksomhetsnummer)
                    assertEquals(event.varselId, it.varselId)
                    assertEquals(
                        varselTilstand.kalkuertSendetidspunkt(
                            ÅpningstiderImpl,
                            hendelseMetadata.timestamp.asOsloLocalDateTime()
                        ), it.varselTimestamp
                    )
                    assertEquals(hendelseMetadata.timestamp, it.kvittertEventTimestamp)
                }
            }
        }
    }


    @Test
    fun `når hendelse er EksterntVarselFeilet med feilkode = 42`() =
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val service = setupService(database, kafka)
            val varselTilstand = varselTilstand(
                uuid("314"),
                SPESIFISERT,
                LocalDateTime.parse("2021-01-01T01:01:01")
            )
            database.insertVarselTilstand(varselTilstand)

            kafka.clear()
            val event = eksterntVarselFeilet("42", uuid("314"))
            service.testProsesserHendelse(event, hendelseMetadata)

            // sender en VarslingStatusDto med status MANGLER_KOFUVI
            assertEquals(1, kafka.history().size)
            kafka.history().first().value().let {
                assertEquals(Status.ANNEN_FEIL, it.status)

                assertEquals(event.virksomhetsnummer, it.virksomhetsnummer)
                assertEquals(event.varselId, it.varselId)
                assertEquals(varselTilstand.data.eksternVarsel.sendeTidspunkt, it.varselTimestamp)
                assertEquals(hendelseMetadata.timestamp, it.kvittertEventTimestamp)
            }
        }

    @Test
    fun `når hendelse er EksterntVarselFeilet men varsel er harddeleted`() =
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val service = setupService(database, kafka)
            kafka.clear()

            service.testProsesserHendelse(
                eksterntVarselFeilet("30308", uuid("314")),
                hendelseMetadata
            )

            // sender ikke VarslingStatusDto
            assertEquals(0, kafka.history().size)
        }

    @Test
    fun `når hendelse er EksterntVarselVellykket men varsel er harddeleted`() =
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val service = setupService(database, kafka)
            kafka.clear()

            service.testProsesserHendelse(
                eksterntVarselVellykket(uuid("314")),
                hendelseMetadata
            )

            // sender ikke VarslingStatusDto
            assertEquals(0, kafka.history().size)
        }

    @Test
    fun `når hendelse er EksterntVarselVellykket`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val service = setupService(database, kafka)
        val varselTilstand = varselTilstand(
            uuid("314"),
            SPESIFISERT,
            LocalDateTime.parse("2021-01-01T01:01:01")
        )
        database.insertVarselTilstand(varselTilstand)

        kafka.clear()
        val event = eksterntVarselVellykket(uuid("314"))
        service.prosesserHendelse(
            event = event,
            meta = hendelseMetadata,
        )

        // sender en VarslingStatusDto med status OK
        assertEquals(1, kafka.history().size)
        kafka.history().first().value().let {
            assertEquals(Status.OK, it.status)

            assertEquals(event.virksomhetsnummer, it.virksomhetsnummer)
            assertEquals(event.varselId, it.varselId)
            assertEquals(varselTilstand.data.eksternVarsel.sendeTidspunkt, it.varselTimestamp)
            assertEquals(hendelseMetadata.timestamp, it.kvittertEventTimestamp)
        }
    }
}

private fun setupService(
    database: Database,
    kafka: MockProducer<String, VarslingStatusDto>
): EksternVarslingStatusEksportService {
    val repository = EksternVarslingRepository(database)
    val service = EksternVarslingStatusEksportService(
        eventSource = object : Hendelsesstrøm {
            override suspend fun forEach(
                stop: AtomicBoolean,
                onTombstone: suspend (UUID) -> Unit,
                body: suspend (HendelseModel.Hendelse, HendelseModel.HendelseMetadata) -> Unit
            ): Unit = TODO("Not yet implemented")
        },
        repo = repository,
        kafka = kafka,
    )
    return service
}

private suspend fun EksternVarslingStatusEksportService.testProsesserHendelse(
    event: HendelseModel.Hendelse,
    hendelseMetadata: HendelseModel.HendelseMetadata
) {
    prosesserHendelse(
        event = event,
        meta = hendelseMetadata,
    )
}

private fun varselTilstand(
    varselId: UUID,
    sendeVindu: HendelseModel.EksterntVarselSendingsvindu,
    sendeTidspunkt: LocalDateTime? = null
) = EksternVarselTilstand.Sendt(
    data = EksternVarselStatiskData(
        varselId = varselId,
        notifikasjonId = uuid("1"),
        produsentId = "1",
        eksternVarsel = EksternVarsel.Sms(
            fnrEllerOrgnr = "",
            sendeVindu = sendeVindu,
            sendeTidspunkt = sendeTidspunkt,
            mobilnummer = "",
            tekst = "",
            ordreId = "123"
        )
    ),
    response = AltinnResponse.Feil(rå = NullNode.instance, feilkode = "", feilmelding = ""),
)

private fun eksterntVarselFeilet(altinnFeilkode: String, varselId: UUID) = HendelseModel.EksterntVarselFeilet(
    virksomhetsnummer = "1",
    notifikasjonId = uuid("1"),
    hendelseId = UUID.randomUUID(),
    produsentId = "1",
    kildeAppNavn = "1",
    varselId = varselId,
    råRespons = NullNode.instance,
    altinnFeilkode = altinnFeilkode,
    feilmelding = "oops"
)


private fun eksterntVarselVellykket(varselId: UUID) = HendelseModel.EksterntVarselVellykket(
    virksomhetsnummer = "1",
    notifikasjonId = uuid("1"),
    hendelseId = UUID.randomUUID(),
    produsentId = "1",
    kildeAppNavn = "1",
    varselId = varselId,
    råRespons = NullNode.instance
)

suspend fun Database.insertVarselTilstand(varselTilstand: EksternVarselTilstand.Sendt) {
    nonTransactionalExecuteUpdate(
        """
                insert into ekstern_varsel_kontaktinfo
                (
                    varsel_id,
                    notifikasjon_id,
                    notifikasjon_opprettet,
                    produsent_id,
                    varsel_type,
                    tlfnr,
                    fnr_eller_orgnr,
                    sms_tekst,
                    sendevindu,
                    sendetidspunkt,
                    state
                )
                values (?, ?, ?, ?, 'SMS', ?, ?, ?, ?, ?, 'NY');
                """
    ) {
        uuid(varselTilstand.data.varselId)
        uuid(varselTilstand.data.notifikasjonId)
        timestamp_without_timezone_utc(Instant.now())
        text(varselTilstand.data.produsentId)
        text("")
        text("")
        text("")
        text(varselTilstand.data.eksternVarsel.sendeVindu.toString())
        nullableText(varselTilstand.data.eksternVarsel.sendeTidspunkt?.toString())
    }

    nonTransactionalExecuteUpdate(
        """ 
                update ekstern_varsel_kontaktinfo
                set 
                    state = '${EksterntVarselTilstand.SENDT}',
                    altinn_response = ?::jsonb,
                    sende_status = ?::status,
                    feilmelding = ?,
                    altinn_feilkode = ?
                where varsel_id = ?
            """
    ) {
        jsonb(varselTilstand.response.rå)
        when (val r = varselTilstand.response) {
            is AltinnResponse.Feil -> {
                text("FEIL")
                nullableText(r.feilmelding)
                nullableText(r.feilkode)
            }

            is AltinnResponse.Ok -> {
                text("OK")
                nullableText(null)
                nullableText(null)
            }

        }
        uuid(varselTilstand.data.varselId)
    }
}
