package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.NotificationsResponse
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.NotificationsResponse.Success.Notification
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.NotificationsResponse.Success.Notification.SendStatus
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Altinn3VarselKlient.OrderStatusResponse.NotificationStatusSummary
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnressursVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselKansellert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOppdatert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds


private class ÅpningstiderMock : Åpningstider {
    val nesteNksÅpningstid = mutableListOf<LocalDateTime>()
    val nesteDagtidIkkeSøndag = mutableListOf<LocalDateTime>()
    override fun nesteNksÅpningstid(start: LocalDateTime) = nesteNksÅpningstid.removeLast()
    override fun nesteDagtidIkkeSøndag(start: LocalDateTime) = nesteDagtidIkkeSøndag.removeLast()
}

private enum class MeldingsType {
    None,
    Altinn2,
    Altinn3,
}

class EksternVarslingServiceTest {
    private val nå = LocalDateTime.parse("2020-01-01T01:01")

    private data class Services(
        val repository: EksternVarslingRepository,
        val service: EksternVarslingService,
        val hendelseProdusent: FakeHendelseProdusent,
        val meldingSendt: AtomicReference<MeldingsType>,
        val åpningstider: ÅpningstiderMock,
    )

    private fun setupService(
        database: Database,
        osloTid: OsloTid = mockOsloTid(nå),
        altinn3VarselKlient: Altinn3VarselKlient? = null,
    ): Services {
        val meldingSendt = AtomicReference(MeldingsType.None)
        val hendelseProdusent = FakeHendelseProdusent()
        val repository = EksternVarslingRepository(database)
        val åpningstider = ÅpningstiderMock()
        val service = EksternVarslingService(
            åpningstider = åpningstider,
            osloTid = osloTid,
            eksternVarslingRepository = repository,
            altinn2VarselKlient = object : Altinn2VarselKlient {
                override suspend fun send(
                    eksternVarsel: EksternVarsel
                ): AltinnVarselKlientResponseOrException {
                    meldingSendt.set(MeldingsType.Altinn2)
                    return AltinnVarselKlientResponse.Ok(rå = NullNode.instance)
                }
            },
            altinn3VarselKlient = altinn3VarselKlient ?: object : Altinn3VarselKlient {
                override suspend fun order(eksternVarsel: EksternVarsel): Altinn3VarselKlient.OrderResponse {
                    meldingSendt.set(MeldingsType.Altinn3)
                    return Altinn3VarselKlient.OrderResponse.Success.fromJson(
                        JsonNodeFactory.instance.objectNode().apply {
                            put("orderId", "fake-${UUID.randomUUID()}")
                        }
                    )
                }

                override suspend fun notifications(orderId: String): NotificationsResponse {
                    return NotificationsResponse.Success(
                        orderId = orderId,
                        generated = 3,
                        succeeded = 3,
                        sendersReference = "fake-${UUID.randomUUID()}",
                        rå = JsonNodeFactory.instance.nullNode(),
                        notifications = listOf(
                            Notification(
                                id = "fake-${UUID.randomUUID()}",
                                succeeded = true,
                                sendStatus = SendStatus(
                                    status = SendStatus.Delivered,
                                    description = "",
                                    lastUpdate = ""
                                ),
                                recipient = Notification.Recipient(
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            )
                        )

                    )
                }

                override suspend fun orderStatus(orderId: String): Altinn3VarselKlient.OrderStatusResponse {
                    return Altinn3VarselKlient.OrderStatusResponse.Success(
                        orderId = orderId,
                        processingStatus = Altinn3VarselKlient.OrderStatusResponse.ProcessingStatus(
                            status = Altinn3VarselKlient.OrderStatusResponse.ProcessingStatus.Completed,
                            description = null,
                            lastUpdate = null,
                        ),
                        rå = JsonNodeFactory.instance.objectNode().apply {
                            put("orderId", orderId)
                        },
                        sendersReference = "fake-${UUID.randomUUID()}",
                        notificationsStatusSummary = NotificationStatusSummary(
                            generated = 3, succeeded = 3,
                        )

                    )
                }
            },
            hendelseProdusent = hendelseProdusent,
            idleSleepDelay = Duration.ZERO,
            recheckEmergencyBrakeDelay = Duration.ZERO,
        )
        return Services(repository, service, hendelseProdusent, meldingSendt, åpningstider)
    }


    @Test
    fun `LØPENDE sendingsvindu`() = runBlocking {
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, hendelseProdusent, meldingSendt) = setupService(database)

            repository.oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        SmsVarselKontaktinfo(
                            varselId = uuid("2"),
                            tlfnr = "",
                            fnrEllerOrgnr = "",
                            smsTekst = "",
                            sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                            sendeTidspunkt = null,
                        )
                    ),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )

            val serviceJob = service.start(this)

            // sends message eventually
            eventually(5.seconds) {
                assertEquals(MeldingsType.Altinn3, meldingSendt.get())
            }

            // message received from kafka
            eventually(20.seconds) {
                val vellykedeVarsler = hendelseProdusent.hendelserOfType<EksterntVarselVellykket>()
                assertFalse(vellykedeVarsler.isEmpty())
            }

            serviceJob.cancel()
        }
    }

    @Test
    fun `NKS_ÅPNINGSTID sendingsvindu innenfor nks åpningstid sendes med en gang`() = runBlocking {
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, _, meldingSendt, åpningstider) = setupService(database)
            repository.oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        EpostVarselKontaktinfo(
                            varselId = uuid("2"),
                            epostAddr = "",
                            fnrEllerOrgnr = "",
                            tittel = "",
                            htmlBody = "",
                            sendevindu = EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        )
                    ),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )
            åpningstider.nesteNksÅpningstid.add(nå.minusMinutes(5))
            val serviceJob = service.start(this)

            // sends message eventually
            eventually(5.seconds) {
                assertEquals(MeldingsType.Altinn3, meldingSendt.get())
            }

            serviceJob.cancel()
        }
    }

    @Test
    fun `NKS_ÅPNINGSTID sendingsvindu utenfor nks åpningstid reskjeddullerres`() = runBlocking {
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, _, _, åpningstider) = setupService(database)
            repository.oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        AltinntjenesteVarselKontaktinfo(
                            varselId = uuid("2"),
                            serviceCode = "1337",
                            serviceEdition = "1",
                            virksomhetsnummer = "",
                            tittel = "",
                            innhold = "",
                            sendevindu = EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                            sendeTidspunkt = null,
                        )
                    ),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )
            åpningstider.nesteNksÅpningstid.add(nå.plusMinutes(5))
            val serviceJob = service.start(this)

            // reschedules
            eventually(20.seconds) {
                assertNotEquals((0 to 0), repository.waitQueueCount())
                assertFalse(
                    database.nonTransactionalExecuteQuery(
                        "select * from wait_queue where varsel_id = '${uuid("2")}'"
                    ) { asMap() }.isEmpty()
                )
                assertTrue(
                    database.nonTransactionalExecuteQuery(
                        "select * from job_queue where varsel_id = '${uuid("2")}'"
                    ) { asMap() }.isEmpty()
                )
            }


            serviceJob.cancel()
        }
    }

    @Test
    fun `DAGTID_IKKE_SØNDAG sendingsvindu innenfor sendes med en gang`() = runBlocking {
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, _, meldingSendt, åpningstider) = setupService(database)
            repository.oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        AltinntjenesteVarselKontaktinfo(
                            varselId = uuid("2"),
                            serviceCode = "1337",
                            serviceEdition = "1",
                            virksomhetsnummer = "",
                            tittel = "",
                            innhold = "",
                            sendevindu = EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG,
                            sendeTidspunkt = null,
                        )
                    ),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )

            åpningstider.nesteDagtidIkkeSøndag.add(nå.minusMinutes(5))
            val serviceJob = service.start(this)

            // sends message eventually
            eventually(10.seconds) {
                assertEquals(MeldingsType.Altinn2, meldingSendt.get())
            }

            serviceJob.cancel()
        }
    }

    @Test
    fun `DAGTID_IKKE_SØNDAG sendingsvindu utenfor reskjedduleres`() = runBlocking {
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, _, _, åpningstider) = setupService(database)
            repository.oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        SmsVarselKontaktinfo(
                            varselId = uuid("2"),
                            tlfnr = "",
                            fnrEllerOrgnr = "",
                            smsTekst = "",
                            sendevindu = EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG,
                            sendeTidspunkt = null,
                        )
                    ),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )
            åpningstider.nesteDagtidIkkeSøndag.add(nå.plusMinutes(5))
            val serviceJob = service.start(this)

            // reskjedduleres
            eventually(5.seconds) {
                assertNotEquals((0 to 0), repository.waitQueueCount())
            }

            serviceJob.cancel()
        }
    }

    @Test
    fun `SPESIFISERT sendingsvindu som har passert sendes med en gang`() = runBlocking {
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, _, meldingSendt) = setupService(database)
            repository.oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        AltinnressursVarselKontaktinfo(
                            varselId = uuid("2"),
                            ressursId = "",
                            virksomhetsnummer = "",
                            epostTittel = "",
                            epostHtmlBody = "",
                            smsTekst = "",
                            sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                            sendeTidspunkt = nå.minusMinutes(1),
                        )
                    ),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )

            val serviceJob = service.start(this)

            // sends message eventually
            eventually(5.seconds) {
                assertEquals(MeldingsType.Altinn3, meldingSendt.get())
            }

            serviceJob.cancel()
        }
    }

    @Test
    fun `SPESIFISERT sendingsvindu som er i fremtid reskjedduleres`() = runBlocking {
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service) = setupService(database)
            repository.oppdaterModellEtterHendelse(
                OppgaveOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        SmsVarselKontaktinfo(
                            varselId = uuid("2"),
                            tlfnr = "",
                            fnrEllerOrgnr = "",
                            smsTekst = "",
                            sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                            sendeTidspunkt = LocalDateTime.now().plusMinutes(5),
                        )
                    ),
                    hardDelete = null,
                    frist = null,
                    påminnelse = null,
                    sakId = null,
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )

            val serviceJob = service.start(this)

            // reschedules
            eventually(5.seconds) {
                assertNotEquals((0 to 0), repository.waitQueueCount())
            }

            serviceJob.cancel()
        }
    }

    @Test
    fun `Eksterne varsler kanselleres`() = runBlocking {
        val gammelVarselId = uuid("2")
        val nyVarselId = uuid("3")
        var mockNow = LocalDateTime.now()

        val osloTid = object : OsloTid {
            override fun localDateTimeNow() = mockNow
            override fun localDateNow() = TODO()
        }
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, hendelseProdusent, meldingSendt) = setupService(database, osloTid)
            repository.oppdaterModellEtterHendelse(EksempelHendelse.SakOpprettet)

            repository.oppdaterModellEtterHendelse(
                KalenderavtaleOpprettet(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    eksternId = "",
                    mottakere = listOf(
                        AltinnMottaker(
                            virksomhetsnummer = "",
                            serviceCode = "",
                            serviceEdition = "",
                        )
                    ),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(
                        SmsVarselKontaktinfo(
                            varselId = gammelVarselId,
                            tlfnr = "",
                            fnrEllerOrgnr = "",
                            smsTekst = "",
                            sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                            sendeTidspunkt = mockNow.plusHours(1),
                        )
                    ),
                    hardDelete = null,
                    påminnelse = null,
                    sakId = EksempelHendelse.SakOpprettet.sakId,
                    erDigitalt = false,
                    lokasjon = null,
                    sluttTidspunkt = null,
                    startTidspunkt = LocalDateTime.now(),
                    tilstand = VENTER_SVAR_FRA_ARBEIDSGIVER
                )
            )

            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
            """
            )

            var serviceJob = service.start(this)

            // sending av varsel skeduleres
            eventually(5.seconds) {
                assertNotEquals((0 to 0), repository.waitQueueCount())
                assertFalse(
                    database.nonTransactionalExecuteQuery(
                        """
                            select * from wait_queue where varsel_id = '$gammelVarselId'
                        """
                    ) { asMap() }.isEmpty()
                )
            }

            repository.oppdaterModellEtterHendelse(
                KalenderavtaleOppdatert(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    grupperingsid = "",
                    tekst = "",
                    lenke = "",
                    eksterneVarsler = listOf(
                        SmsVarselKontaktinfo(
                            varselId = nyVarselId,
                            tlfnr = "",
                            fnrEllerOrgnr = "",
                            smsTekst = "",
                            sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                            sendeTidspunkt = null,
                        )
                    ),
                    hardDelete = null,
                    påminnelse = null,
                    erDigitalt = false,
                    lokasjon = null,
                    sluttTidspunkt = null,
                    startTidspunkt = LocalDateTime.now(),
                    tilstand = VENTER_SVAR_FRA_ARBEIDSGIVER,
                    idempotenceKey = null,
                    oppdatertTidspunkt = Instant.parse("2020-01-01T10:15:30.00Z"),
                    opprettetTidspunkt = Instant.parse("2020-01-01T00:01:00.00Z"),
                )
            )

            // melding sendes til kafka
            eventually(5.seconds) {
                assertEquals(MeldingsType.Altinn3, meldingSendt.get())
            }

            // ny varsel er sendt vellykket
            eventually(2.seconds) {
                val velykkedeVarsler = hendelseProdusent.hendelserOfType<EksterntVarselVellykket>()
                assertFalse(velykkedeVarsler.isEmpty())
                assertEquals(nyVarselId, velykkedeVarsler.first().varselId)
            }

            // gammel varsel er fortsatt i wait_queue
            eventually(2.seconds) {
                assertFalse(
                    database.nonTransactionalExecuteQuery<Map<String, Any>>(
                        """
                            select * from wait_queue where varsel_id = '$gammelVarselId'
                        """
                    ) { asMap() }.isEmpty()
                )
            }

            // trigger rescume scheduled work
            mockNow = mockNow.plusHours(2)
            serviceJob.cancel()
            serviceJob = service.start(this)

            // gammel varsel kanselleres
            eventually(2.seconds) {
                val kansellerteVarsler = hendelseProdusent.hendelserOfType<EksterntVarselKansellert>()
                assertFalse(kansellerteVarsler.isEmpty())
                assertEquals(gammelVarselId, kansellerteVarsler.first().varselId)
            }

            serviceJob.cancel()
        }
    }

    @Test
    fun `Altinn3 varsel oppførsel status simulering`() = runBlocking {
        val notificationsNewRå = laxObjectMapper.readValue<JsonNode>(
            """
                {
                "generated": 2,
                "notifications": [
                  {
                    "id": "042f1a8a-fe29-47c9-a992-433399563c12",
                    "recipient": {
                      "emailAddress": "abc@a.no",
                      "organizationNumber": "12341234"
                    },
                    "sendStatus": {
                      "description": "The email was successfully delivered to the recipient. No errors were reported, indicating successful delivery.",
                      "lastUpdate": "2025-03-31T16:21:04.12234Z",
                      "status": "Delivered"
                    },
                    "succeeded": true
                  },
                  {
                    "id": "6b3bfbc4-a092-4df7-8f38-e43171df49a9",
                    "recipient": {
                      "emailAddress": "sss.sss@sss.no",
                      "organizationNumber": "12341234"
                    },
                    "sendStatus": {
                      "description": "The email has been created, but has not been picked up for processing yet.",
                      "lastUpdate": "2025-03-31T16:21:04.126885Z",
                      "status": "New"
                    },
                    "succeeded": false
                  }
                ],
                "orderId": "70bc6dff-0c14-4842-b36a-3ab64a2dae08",
                "succeeded": 1
              }
            """.trimIndent()
        )
        val notificationsDeliveredRå = laxObjectMapper.readValue<JsonNode>(
            """
                {
                "generated": 2,
                "notifications": [
                  {
                    "id": "042f1a8a-fe29-47c9-a992-433399563c12",
                    "recipient": {
                      "emailAddress": "abc@a.no",
                      "organizationNumber": "12341234"
                    },
                    "sendStatus": {
                      "description": "The email was successfully delivered to the recipient. No errors were reported, indicating successful delivery.",
                      "lastUpdate": "2025-03-31T16:21:04.12234Z",
                      "status": "Delivered"
                    },
                    "succeeded": true
                  },
                  {
                    "id": "6b3bfbc4-a092-4df7-8f38-e43171df49a9",
                    "recipient": {
                      "emailAddress": "sss.sss@sss.no",
                      "organizationNumber": "12341234"
                    },
                    "sendStatus": {
                      "description": "The email was successfully delivered to the recipient. No errors were reported, indicating successful delivery.",
                      "lastUpdate": "2025-03-31T16:21:04.126885Z",
                      "status": "Delivered"
                    },
                    "succeeded": true
                  }
                ],
                "orderId": "70bc6dff-0c14-4842-b36a-3ab64a2dae08",
                "succeeded": 2
              }
            """.trimIndent()
        )
        val altinn3VarselKlient: Altinn3VarselKlient = object : Altinn3VarselKlient {
            val orderStatusResolvers = mutableListOf(
                { orderId: String ->
                    Altinn3VarselKlient.OrderStatusResponse.Success(
                        rå = JsonNodeFactory.instance.objectNode(),
                        orderId = orderId,
                        sendersReference = null,
                        Altinn3VarselKlient.OrderStatusResponse.ProcessingStatus(
                            Altinn3VarselKlient.OrderStatusResponse.ProcessingStatus.Processing,
                            null,
                            null
                        ),
                        NotificationStatusSummary(0, 0)
                    )
                },
                { orderId: String ->
                    Altinn3VarselKlient.OrderStatusResponse.Success(
                        rå = JsonNodeFactory.instance.objectNode(),
                        orderId = orderId,
                        sendersReference = null,
                        Altinn3VarselKlient.OrderStatusResponse.ProcessingStatus(
                            Altinn3VarselKlient.OrderStatusResponse.ProcessingStatus.Completed,
                            null,
                            null
                        ),
                        NotificationStatusSummary(0, 0)
                    )
                },
            )
            val notificationResolvers = mutableListOf(
                {
                    NotificationsResponse.Success.fromJson(notificationsNewRå)
                },
                {
                    NotificationsResponse.Success.fromJson(notificationsDeliveredRå)
                },
            )


            override suspend fun order(eksternVarsel: EksternVarsel): Altinn3VarselKlient.OrderResponse {
                val ordreId = UUID.randomUUID()
                return Altinn3VarselKlient.OrderResponse.Success(
                    orderId = "$ordreId",
                    rå = JsonNodeFactory.instance.objectNode().put("orderId", ordreId.toString())
                )
            }

            override suspend fun notifications(orderId: String): NotificationsResponse {
                return if (notificationResolvers.size > 1) notificationResolvers.removeAt(0)
                    .invoke() else notificationResolvers.first().invoke()
            }

            override suspend fun orderStatus(orderId: String): Altinn3VarselKlient.OrderStatusResponse {
                return if (orderStatusResolvers.size > 1) orderStatusResolvers.removeAt(0)
                    .invoke(orderId) else orderStatusResolvers.first().invoke(orderId)
            }
        }

        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, hendelseProdusent) = setupService(
                database,
                altinn3VarselKlient = altinn3VarselKlient
            )
            repository.oppdaterModellEtterHendelse(oppgave)

            assertEquals(1, repository.jobQueueCount())
            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
                """
            )
            val job = service.start(this)
            eventually(5.seconds) {
                val vellykket = hendelseProdusent.hendelserOfType<EksterntVarselVellykket>()
                assertEquals(1, vellykket.size)
                assertEquals(notificationsDeliveredRå, vellykket.first().råRespons)
            }
            job.cancel()
        }
    }

    @Test
    fun `Altinn3 varsel oppførsel status simulering (synkron feil)`() = runBlocking {
        val altinn3VarselKlient: Altinn3VarselKlient = object : Altinn3VarselKlient {
            override suspend fun order(eksternVarsel: EksternVarsel): Altinn3VarselKlient.OrderResponse {
                return Altinn3VarselKlient.ErrorResponse(
                    message = """
                            Bad Request: {
                              "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
                              "title": "One or more validation errors occurred.",
                              "status": 400,
                              "errors": {
                                "Recipients[0].EmailAddress": [
                                  "Invalid email address format."
                                ]
                              },
                              "traceId": "00-75d1ef6967946ee897e3370f30d9eec0-92e66f45cf453857-01"
                            }
                        """.trimIndent(),
                    code = "400",
                    rå = laxObjectMapper.readValue(
                        """
                            {
                              "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
                              "title": "One or more validation errors occurred.",
                              "errors": {
                                "Recipients[0].EmailAddress": [
                                  "Invalid email address format."
                                ]
                              },
                              "status": 400,
                              "traceId": "00-75d1ef6967946ee897e3370f30d9eec0-92e66f45cf453857-01"
                            }
                        """.trimIndent()
                    )
                )
            }

            override suspend fun notifications(orderId: String) = TODO("Not yet implemented")
            override suspend fun orderStatus(orderId: String) = TODO("Not yet implemented")
        }
        withTestDatabase(EksternVarsling.databaseConfig) { database ->
            val (repository, service, hendelseProdusent) = setupService(
                database,
                altinn3VarselKlient = altinn3VarselKlient
            )
            repository.oppdaterModellEtterHendelse(oppgave)

            assertEquals(1, repository.jobQueueCount())
            database.nonTransactionalExecuteUpdate(
                """
                update emergency_break set stop_processing = false where id = 0
                """
            )
            val job = service.start(this)
            eventually(5.seconds) {
                assertEquals(1, hendelseProdusent.hendelser.size)
                val vellykket = hendelseProdusent.hendelserOfType<EksterntVarselFeilet>()
                assertEquals(1, vellykket.size)
            }
            job.cancel()
        }
    }
}

private val oppgave = OppgaveOpprettet(
    virksomhetsnummer = "1",
    notifikasjonId = uuid("1"),
    hendelseId = uuid("1"),
    produsentId = "",
    kildeAppNavn = "",
    merkelapp = "",
    eksternId = "",
    mottakere = listOf(
        AltinnMottaker(
            virksomhetsnummer = "",
            serviceCode = "",
            serviceEdition = "",
        )
    ),
    tekst = "",
    grupperingsid = "",
    lenke = "",
    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
    eksterneVarsler = listOf(
        SmsVarselKontaktinfo(
            varselId = uuid("2"),
            tlfnr = "",
            fnrEllerOrgnr = "",
            smsTekst = "",
            sendevindu = EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null,
        )
    ),
    hardDelete = null,
    frist = null,
    påminnelse = null,
    sakId = null,
)

fun mockOsloTid(mockNow: LocalDateTime) = object : OsloTid {
    override fun localDateTimeNow() = mockNow
    override fun localDateNow() = mockNow.toLocalDate()
}

suspend fun eventually(duration: kotlin.time.Duration, block: suspend () -> Unit) {
    val end = Instant.now().plusMillis(duration.inWholeMilliseconds)
    while (Instant.now().isBefore(end)) {
        try {
            block()
            return
        } catch (e: Throwable) {
            // ignore
        }
        delay(50)
    }
    block()
}

