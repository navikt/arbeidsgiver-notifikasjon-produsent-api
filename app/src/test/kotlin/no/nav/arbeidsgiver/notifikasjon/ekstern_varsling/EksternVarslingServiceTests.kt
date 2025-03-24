package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnressursVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
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
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference
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

class EksternVarslingServiceTests : DescribeSpec({
    val nå = LocalDateTime.parse("2020-01-01T01:01")

    data class Services(
        val database: Database,
        val repository: EksternVarslingRepository,
        val service: EksternVarslingService,
        val hendelseProdusent: FakeHendelseProdusent,
        val meldingSendt: AtomicReference<MeldingsType>,
        val åpningstider: ÅpningstiderMock,
    )

    fun DescribeSpec.setupService(
        osloTid: OsloTid = mockOsloTid(nå)
    ): Services {
        val meldingSendt = AtomicReference(MeldingsType.None)
        val hendelseProdusent = FakeHendelseProdusent()
        val database = testDatabase(EksternVarsling.databaseConfig)
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
            altinn3VarselKlient = object : Altinn3VarselKlient {
                override suspend fun order(eksternVarsel: EksternVarsel): Altinn3VarselKlient.OrderResponse {
                    meldingSendt.set(MeldingsType.Altinn3)
                    return Altinn3VarselKlient.OrderResponse.Success.fromJson(
                        JsonNodeFactory.instance.objectNode().apply {
                            put("orderId", "fake-${UUID.randomUUID()}")
                        }
                    )
                }

                override suspend fun notifications(orderId: String): Altinn3VarselKlient.NotificationsResponse {
                    TODO("Not yet implemented")
                }

                override suspend fun orderStatus(orderId: String): Altinn3VarselKlient.OrderStatusResponse {
                    TODO("Not yet implemented")
                }
            },
            hendelseProdusent = hendelseProdusent,
            idleSleepDelay = Duration.ZERO,
            recheckEmergencyBrakeDelay = Duration.ZERO,
        )
        return Services(database, repository, service, hendelseProdusent, meldingSendt, åpningstider)
    }


    describe("EksternVarslingService#start()") {
        context("LØPENDE sendingsvindu") {
            val (database, repository, service, hendelseProdusent, meldingSendt) = setupService()

            repository.oppdaterModellEtterHendelse(OppgaveOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "",
                kildeAppNavn = "",
                merkelapp = "",
                eksternId = "",
                mottakere = listOf(AltinnMottaker(
                    virksomhetsnummer = "",
                    serviceCode = "",
                    serviceEdition = "",
                )),
                tekst = "",
                grupperingsid = "",
                lenke = "",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                eksterneVarsler = listOf(SmsVarselKontaktinfo(
                    varselId = uuid("2"),
                    tlfnr = "",
                    fnrEllerOrgnr = "",
                    smsTekst = "",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null,
                )),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(5.seconds) {
                    meldingSendt.get() shouldBe MeldingsType.Altinn3
                }
            }

            it("message received from kafka") {
                eventually(20.seconds) {
                    val vellykedeVarsler = hendelseProdusent.hendelserOfType<EksterntVarselVellykket>()
                    vellykedeVarsler shouldNot beEmpty()
                }
            }

            serviceJob.cancel()
        }

        context("NKS_ÅPNINGSTID sendingsvindu innenfor nks åpningstid sendes med en gang") {
            val (database, repository, service, _, meldingSendt, åpningstider) = setupService()
            repository.oppdaterModellEtterHendelse(OppgaveOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "",
                kildeAppNavn = "",
                merkelapp = "",
                eksternId = "",
                mottakere = listOf(AltinnMottaker(
                    virksomhetsnummer = "",
                    serviceCode = "",
                    serviceEdition = "",
                )),
                tekst = "",
                grupperingsid = "",
                lenke = "",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                eksterneVarsler = listOf(EpostVarselKontaktinfo(
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
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)
            åpningstider.nesteNksÅpningstid.add(nå.minusMinutes(5))
            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(5.seconds) {
                    meldingSendt.get() shouldBe MeldingsType.Altinn3
                }
            }

            serviceJob.cancel()
        }

        context("NKS_ÅPNINGSTID sendingsvindu utenfor nks åpningstid reskjeddullerres") {
            val (database, repository, service, _, _, åpningstider) = setupService()
            repository.oppdaterModellEtterHendelse(OppgaveOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "",
                kildeAppNavn = "",
                merkelapp = "",
                eksternId = "",
                mottakere = listOf(AltinnMottaker(
                    virksomhetsnummer = "",
                    serviceCode = "",
                    serviceEdition = "",
                )),
                tekst = "",
                grupperingsid = "",
                lenke = "",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                eksterneVarsler = listOf(AltinntjenesteVarselKontaktinfo(
                    varselId = uuid("2"),
                    serviceCode = "1337",
                    serviceEdition = "1",
                    virksomhetsnummer = "",
                    tittel = "",
                    innhold = "",
                    sendevindu = EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                    sendeTidspunkt = null,
                )),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)
            åpningstider.nesteNksÅpningstid.add(nå.plusMinutes(5))
            val serviceJob = service.start(this)

            it("reschedules") {
                eventually(20.seconds) {
                    repository.waitQueueCount() shouldNotBe (0 to 0)
                    database.nonTransactionalExecuteQuery("""
                        select * from wait_queue where varsel_id = '${uuid("2")}'
                    """) { asMap() } shouldNot beEmpty()
                    database.nonTransactionalExecuteQuery(
                        """
                        select * from job_queue where varsel_id = '${uuid("2")}'
                    """
                    ) { asMap() } should beEmpty()
                }
            }


            serviceJob.cancel()
        }

        context("DAGTID_IKKE_SØNDAG sendingsvindu innenfor sendes med en gang") {
            val (database, repository, service, _, meldingSendt, åpningstider) = setupService()
            repository.oppdaterModellEtterHendelse(OppgaveOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "",
                kildeAppNavn = "",
                merkelapp = "",
                eksternId = "",
                mottakere = listOf(AltinnMottaker(
                    virksomhetsnummer = "",
                    serviceCode = "",
                    serviceEdition = "",
                )),
                tekst = "",
                grupperingsid = "",
                lenke = "",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                eksterneVarsler = listOf(AltinntjenesteVarselKontaktinfo(
                    varselId = uuid("2"),
                    serviceCode = "1337",
                    serviceEdition = "1",
                    virksomhetsnummer = "",
                    tittel = "",
                    innhold = "",
                    sendevindu = EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG,
                    sendeTidspunkt = null,
                )),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            åpningstider.nesteDagtidIkkeSøndag.add(nå.minusMinutes(5))
            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(10.seconds) {
                    meldingSendt.get() shouldBe MeldingsType.Altinn2
                }
            }

            serviceJob.cancel()
        }

        context("DAGTID_IKKE_SØNDAG sendingsvindu utenfor reskjedduleres") {
            val (database, repository, service, _, _, åpningstider) = setupService()
            repository.oppdaterModellEtterHendelse(OppgaveOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "",
                kildeAppNavn = "",
                merkelapp = "",
                eksternId = "",
                mottakere = listOf(AltinnMottaker(
                    virksomhetsnummer = "",
                    serviceCode = "",
                    serviceEdition = "",
                )),
                tekst = "",
                grupperingsid = "",
                lenke = "",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                eksterneVarsler = listOf(SmsVarselKontaktinfo(
                    varselId = uuid("2"),
                    tlfnr = "",
                    fnrEllerOrgnr = "",
                    smsTekst = "",
                    sendevindu = EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG,
                    sendeTidspunkt = null,
                )),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)
            åpningstider.nesteDagtidIkkeSøndag.add(nå.plusMinutes(5))
            val serviceJob = service.start(this)

            it("reskjedduleres") {
                eventually(5.seconds) {
                    repository.waitQueueCount() shouldNotBe (0 to 0)
                }
            }

            serviceJob.cancel()
        }

        context("SPESIFISERT sendingsvindu som har passert sendes med en gang") {
            val (database, repository, service, _, meldingSendt) = setupService()
            repository.oppdaterModellEtterHendelse(OppgaveOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "",
                kildeAppNavn = "",
                merkelapp = "",
                eksternId = "",
                mottakere = listOf(AltinnMottaker(
                    virksomhetsnummer = "",
                    serviceCode = "",
                    serviceEdition = "",
                )),
                tekst = "",
                grupperingsid = "",
                lenke = "",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                eksterneVarsler = listOf(AltinnressursVarselKontaktinfo(
                    varselId = uuid("2"),
                    ressursId = "",
                    virksomhetsnummer = "",
                    epostTittel = "",
                    epostHtmlBody = "",
                    smsTekst = "",
                    sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                    sendeTidspunkt = nå.minusMinutes(1),
                )),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(5.seconds) {
                    meldingSendt.get() shouldBe MeldingsType.Altinn3
                }
            }

            serviceJob.cancel()
        }

        context("SPESIFISERT sendingsvindu som er i fremtid reskjedduleres") {
            val (database, repository, service) = setupService()
            repository.oppdaterModellEtterHendelse(OppgaveOpprettet(
                virksomhetsnummer = "1",
                notifikasjonId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "",
                kildeAppNavn = "",
                merkelapp = "",
                eksternId = "",
                mottakere = listOf(AltinnMottaker(
                    virksomhetsnummer = "",
                    serviceCode = "",
                    serviceEdition = "",
                )),
                tekst = "",
                grupperingsid = "",
                lenke = "",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                eksterneVarsler = listOf(SmsVarselKontaktinfo(
                    varselId = uuid("2"),
                    tlfnr = "",
                    fnrEllerOrgnr = "",
                    smsTekst = "",
                    sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                    sendeTidspunkt = LocalDateTime.now().plusMinutes(5),
                )),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            val serviceJob = service.start(this)

            it("reschedules") {
                eventually(5.seconds) {
                    repository.waitQueueCount() shouldNotBe (0 to 0)
                }
            }

            serviceJob.cancel()
        }

        context("Eksterne varsler kanselleres") {
            val gammelVarselId = uuid("2")
            val nyVarselId = uuid("3")
            var mockNow = LocalDateTime.now()

            val osloTid = object : OsloTid {
                override fun localDateTimeNow() = mockNow
                override fun localDateNow() = TODO()
            }
            val (database, repository, service, hendelseProdusent, meldingSendt) = setupService(osloTid)
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
                    mottakere = listOf(AltinnMottaker(
                        virksomhetsnummer = "",
                        serviceCode = "",
                        serviceEdition = "",
                    )),
                    tekst = "",
                    grupperingsid = "",
                    lenke = "",
                    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                    eksterneVarsler = listOf(SmsVarselKontaktinfo(
                        varselId = gammelVarselId,
                        tlfnr = "",
                        fnrEllerOrgnr = "",
                        smsTekst = "",
                        sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                        sendeTidspunkt = mockNow.plusHours(1),
                    )),
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

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            var serviceJob = service.start(this)

            it("sending av varsel skeduleres") {
                eventually(5.seconds) {
                    repository.waitQueueCount() shouldNotBe (0 to 0)
                    database.nonTransactionalExecuteQuery("""
                        select * from wait_queue where varsel_id = '$gammelVarselId'
                    """) { asMap() } shouldNot beEmpty()
                }
            }

            repository.oppdaterModellEtterHendelse(KalenderavtaleOppdatert(
                    virksomhetsnummer = "1",
                    notifikasjonId = uuid("1"),
                    hendelseId = uuid("1"),
                    produsentId = "",
                    kildeAppNavn = "",
                    merkelapp = "",
                    grupperingsid = "",
                    tekst = "",
                    lenke = "",
                    eksterneVarsler = listOf(SmsVarselKontaktinfo(
                        varselId = nyVarselId,
                        tlfnr = "",
                        fnrEllerOrgnr = "",
                        smsTekst = "",
                        sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                        sendeTidspunkt = null,
                    )),
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
            ))

            it("melding sendes til kafka") {
                eventually(5.seconds) {
                    meldingSendt.get() shouldBe MeldingsType.Altinn3
                }
            }

            it("ny varsel er sendt vellykket") {
                eventually(2.seconds) {
                    val velykkedeVarsler = hendelseProdusent.hendelserOfType<EksterntVarselVellykket>()
                    velykkedeVarsler shouldNot beEmpty()
                    velykkedeVarsler.first().varselId shouldBe nyVarselId
                }
            }

            it("gammel varsel er fortsatt i wait_queue") {
                eventually(2.seconds) {
                    database.nonTransactionalExecuteQuery<Map<String, Any>>(
                        """
                        select * from wait_queue where varsel_id = '$gammelVarselId'
                    """
                    ) { asMap() } shouldNot beEmpty()
                }
            }

            // trigger rescume scheduled work
            mockNow = mockNow.plusHours(2)
            serviceJob.cancel()
            serviceJob = service.start(this)

            it("gammel varsel kanselleres") {
                eventually(2.seconds) {
                    val kansellerteVarsler = hendelseProdusent.hendelserOfType<EksterntVarselKansellert>()
                    kansellerteVarsler shouldNot beEmpty()
                    kansellerteVarsler.first().varselId shouldBe gammelVarselId
                }
            }

            serviceJob.cancel()
        }
    }
})

fun mockOsloTid(mockNow: LocalDateTime) = object : OsloTid {
    override fun localDateTimeNow() = mockNow
    override fun localDateNow() = mockNow.toLocalDate()
}

