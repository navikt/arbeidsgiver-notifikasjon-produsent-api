package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.arbeidsgiver.notifikasjon.EksternVarsling
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.tid.LokalOsloTid
import no.nav.arbeidsgiver.notifikasjon.util.StubbedHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EksternVarslingServiceTests : DescribeSpec({
    val database = testDatabase(EksternVarsling.databaseConfig)
    val repository = EksternVarslingRepository(database)
    val hendelseProdusent = StubbedHendelseProdusent()
    val meldingSendt = AtomicBoolean(false)

    val service = EksternVarslingService(
        eksternVarslingRepository = repository,
        altinnVarselKlient = object: AltinnVarselKlient {
            override suspend fun send(
                eksternVarsel: EksternVarsel
            ): Result<AltinnVarselKlient.AltinnResponse> {
                meldingSendt.set(true)
                return Result.success(AltinnVarselKlient.AltinnResponse.Ok(rå = NullNode.instance))
            }
        },
        hendelseProdusent = hendelseProdusent,
    )

    val nå = LocalDateTime.parse("2020-01-01T01:01")
    beforeEach {
        /**
         * uten before each mister vi mockObject oppførsel i påfølgende av testene. litt usikker på hvorfor
         */
        mockkObject(LokalOsloTid)
        every { LokalOsloTid.now() } returns nå
    }
    afterEach {
        unmockkAll()
    }

    describe("EksternVarslingService#start()") {
        context("LØPENDE sendingsvindu") {
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
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    meldingSendt.get() shouldBe true
                }
            }

            it("message received from kafka") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    val vellykedeVarsler = hendelseProdusent.hendelserOfType<EksterntVarselVellykket>()
                    vellykedeVarsler shouldNot beEmpty()
                }
            }

            serviceJob.cancel()
        }

        context("NKS_ÅPNINGSTID sendingsvindu innenfor nks åpningstid sendes med en gang") {
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
                    sendevindu = EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                    sendeTidspunkt = null,
                )),
                hardDelete = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)
            mockkObject(Åpningstider)
            every { Åpningstider.nesteNksÅpningstid() } returns nå.minusMinutes(5)
            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    meldingSendt.get() shouldBe true
                }
            }

            serviceJob.cancel()
        }

        context("NKS_ÅPNINGSTID sendingsvindu utenfor nks åpningstid reskjeddullerres") {
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
                    sendevindu = EksterntVarselSendingsvindu.NKS_ÅPNINGSTID,
                    sendeTidspunkt = null,
                )),
                hardDelete = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)
            mockkObject(Åpningstider)
            every { Åpningstider.nesteNksÅpningstid() } returns nå.plusMinutes(5)
            val serviceJob = service.start(this)

            it("reschedules") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    repository.waitQueueCount() shouldBe 1
                }
            }

            serviceJob.cancel()
        }

        context("DAGTID_IKKE_SØNDAG sendingsvindu innenfor sendes med en gang") {
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
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)
            mockkObject(Åpningstider)
            every { Åpningstider.nesteDagtidIkkeSøndag() } returns nå.minusMinutes(5)
            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    meldingSendt.get() shouldBe true
                }
            }

            serviceJob.cancel()
        }

        context("DAGTID_IKKE_SØNDAG sendingsvindu utenfor reskjedduleres") {
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
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)
            mockkObject(Åpningstider)
            every { Åpningstider.nesteDagtidIkkeSøndag() } returns nå.plusMinutes(5)
            val serviceJob = service.start(this)

            it("reskjedduleres") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    repository.waitQueueCount() shouldBe 1
                }
            }

            serviceJob.cancel()
        }

        context("SPESIFISERT sendingsvindu som har passert sendes med en gang") {
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
                    sendeTidspunkt = LocalDateTime.now().minusMinutes(5),
                )),
                hardDelete = null,
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    meldingSendt.get() shouldBe true
                }
            }

            serviceJob.cancel()
        }

        context("SPESIFISERT sendingsvindu som er i fremtid reskjedduleres") {
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
            ))

            database.nonTransactionalExecuteUpdate("""
                update emergency_break set stop_processing = false where id = 0
            """)

            val serviceJob = service.start(this)

            it("reschedules") {
                eventually(kotlin.time.Duration.seconds(5)) {
                    repository.waitQueueCount() shouldBe 1
                }
            }

            serviceJob.cancel()
        }
    }
})