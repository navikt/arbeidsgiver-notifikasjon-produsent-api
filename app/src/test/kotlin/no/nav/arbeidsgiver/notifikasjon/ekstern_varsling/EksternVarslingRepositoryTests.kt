package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.OffsetDateTime

class EksternVarslingRepositoryTests: DescribeSpec({
    val database = testDatabase(EksternVarsling.databaseConfig)
    val repository = EksternVarslingRepository(database)

    val oppgaveOpprettet = Hendelse.OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
        mottaker = AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        ),
        tekst = "1",
        grupperingsid = null,
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
        eksterneVarsler = listOf(
            SmsVarselKontaktinfo(
                varselId = uuid("3"),
                fnrEllerOrgnr = "1",
                tlfnr = "1",
                smsTekst = "hey",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            EpostVarselKontaktinfo(
                varselId = uuid("4"),
                fnrEllerOrgnr = "1",
                epostAddr = "1",
                tittel = "hey",
                htmlBody = "body",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            )
        )
    )

    describe("Getting and deleting jobs") {
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        val id1 = repository.findWork(lockTimeout = Duration.ofMinutes(1))

        it("should pick and delete a job") {
            id1 shouldNot beNull()
            id1 shouldBeIn listOf(uuid("3"), uuid("4"))
        }
        repository.deleteFromWorkQueue(id1!!)

        val id2 = repository.findWork(lockTimeout = Duration.ofMinutes(1))
        it("should pick and delete another job") {
            id2 shouldNot beNull()
            id1 shouldNotBe id2
            id2 shouldBeIn listOf(uuid("3"), uuid("4"))
        }
        repository.deleteFromWorkQueue(id2!!)

        val id3 = repository.findWork(lockTimeout = Duration.ofMinutes(1))
        it("should be no more jobs to pick") {
            id3 should beNull()
        }
    }

    describe("Can't pick a job while locked") {
        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findWork(lockTimeout = Duration.ofDays(1))
        val id2 = repository.findWork(lockTimeout = Duration.ofDays(1))

        it("should pick up the single job we have") {
            id1 shouldBe uuid("3")
        }

        it("should not pick up the single, locked job") {
            id2 should beNull()
        }
    }

    describe("auto-release locks") {
        it("release time-out") {
            repository.oppdaterModellEtterHendelse(
                oppgaveOpprettet.copy(
                    eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
                )
            )

            val id1 = repository.findWork(lockTimeout = Duration.ofMillis(1))
            delay(Duration.ofMillis(100).toMillis())
            val abandonedLocks = repository.releaseAbandonedLocks()

            abandonedLocks shouldHaveSize 1
            abandonedLocks[0].varselId shouldBe id1

            val id2 = repository.findWork(lockTimeout = Duration.ofMinutes(1))
            id1 shouldBe id2
        }
    }


    describe("release and reacquire lock") {
        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findWork(lockTimeout = Duration.ofMinutes(1))!!

        it("should get the job") {
            id1 shouldBe uuid("3")
        }

        it("should be returned to work queue") {
            repository.returnToWorkQueue(id1)
        }

        val id2 = repository.findWork(lockTimeout = Duration.ofMinutes(1))!!
        it("should get it again") {
            id2 shouldBe uuid("3")
        }
    }
})
