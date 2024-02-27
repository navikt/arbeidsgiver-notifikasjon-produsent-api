package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class EksternVarslingServiceTests : DescribeSpec({
    val hendelseProdusent = FakeHendelseProdusent()
    val meldingSendt = AtomicBoolean(false)
    val nå = LocalDateTime.parse("2020-01-01T01:01")
    val åpningstider = object : Åpningstider {
        val nesteNksÅpningstid = mutableListOf<LocalDateTime>()
        val nesteDagtidIkkeSøndag = mutableListOf<LocalDateTime>()
        override fun nesteNksÅpningstid(start: LocalDateTime) = nesteNksÅpningstid.removeLast()
        override fun nesteDagtidIkkeSøndag(start: LocalDateTime) = nesteDagtidIkkeSøndag.removeLast()

    }

    fun DescribeSpec.setupService(): Triple<Database, EksternVarslingRepository, EksternVarslingService> {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)
        val service = EksternVarslingService(
            åpningstider = åpningstider,
            osloTid = object : OsloTid {
                override fun localDateTimeNow() = nå
                override fun localDateNow() = TODO("Not yet implemented")
            },
            eksternVarslingRepository = repository,
            altinnVarselKlient = object : AltinnVarselKlient {
                override suspend fun send(
                    eksternVarsel: EksternVarsel
                ): AltinnVarselKlientResponseOrException {
                    meldingSendt.set(true)
                    return AltinnVarselKlientResponse.Ok(rå = NullNode.instance)
                }
            },
            hendelseProdusent = hendelseProdusent,
            idleSleepDelay = Duration.ZERO,
            recheckEmergencyBrakeDelay = Duration.ZERO,
        )
        return Triple(database, repository, service)
    }


    describe("EksternVarslingService#start()") {
        context("LØPENDE sendingsvindu") {
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
                    meldingSendt.get() shouldBe true
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
            åpningstider.nesteNksÅpningstid.add(nå.minusMinutes(5))
            val serviceJob = service.start(this)

            it("sends message eventually") {
                eventually(5.seconds) {
                    meldingSendt.get() shouldBe true
                }
            }

            serviceJob.cancel()
        }

        context("NKS_ÅPNINGSTID sendingsvindu utenfor nks åpningstid reskjeddullerres") {
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
                eventually(5.seconds) {
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
                eventually(5.seconds) {
                    meldingSendt.get() shouldBe true
                }
            }

            serviceJob.cancel()
        }

        context("DAGTID_IKKE_SØNDAG sendingsvindu utenfor reskjedduleres") {
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
                    sendeTidspunkt = LocalDateTime.now().minusMinutes(5),
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
                    meldingSendt.get() shouldBe true
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
    }
})

private fun ResultSet.asMap() = (1..this.metaData.columnCount).associate {
    this.metaData.getColumnName(it) to this.getObject(it)
}