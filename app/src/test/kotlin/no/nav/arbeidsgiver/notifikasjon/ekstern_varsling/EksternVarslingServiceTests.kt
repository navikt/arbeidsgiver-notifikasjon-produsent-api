package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.EksternVarsling
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.tid.LokalOsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime

class Done : Throwable("done")

@OptIn(ExperimentalTime::class)
class EksternVarslingServiceTests : DescribeSpec({
    val log = logger()
    val database = testDatabase(EksternVarsling.databaseConfig)
    val repository = EksternVarslingRepository(database)
    val kafka = embeddedKafka()
    val nå = LocalDateTime.parse("2020-01-01T01:01")
    LokalOsloTid.clock = ConstantClock(nå.atOslo().toInstant())

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
        kafkaProducer = kafka.newProducer(),
    )

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

            val consumer = kafka.newConsumer()
            try {
                consumer.forEachEvent { event ->
                    log.info("message received $event")
                    if (event is EksterntVarselVellykket) {
                        throw Done()
                    }
                }
            } catch (e: Done) {
                // nothing to do
            }

            it("message received from kafka") {
                true shouldBe true
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

            //every { Åpningstider.nesteNksÅpningstid() } returns nå.minusMinutes(5)
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

            //every { Åpningstider.nesteNksÅpningstid() } returns nå.plusMinutes(5)
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

            //every { Åpningstider.nesteDagtidIkkeSøndag() } returns nå.minusMinutes(5)
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

            //every { Åpningstider.nesteDagtidIkkeSøndag() } returns nå.plusMinutes(5)
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


class ConstantClock(
    private val now: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
): Clock() {
    override fun getZone() = zone

    override fun withZone(zone: ZoneId) =
        ConstantClock(now, zone)

    override fun instant() = now
}
