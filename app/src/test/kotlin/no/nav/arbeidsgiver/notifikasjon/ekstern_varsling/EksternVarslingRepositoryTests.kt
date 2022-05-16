package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

class EksternVarslingRepositoryTests: DescribeSpec({
    val database = testDatabase(EksternVarsling.databaseConfig)
    val repository = EksternVarslingRepository(database)

    val oppgaveOpprettet = OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
        mottakere = listOf(AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )),
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
        ),
        hardDelete = null,
    )

    describe("Getting and deleting jobs") {
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        val id1 = repository.findJob(lockTimeout = Duration.ofMinutes(1))

        it("should pick and delete a job") {
            id1 shouldNot beNull()
            id1 shouldBeIn listOf(uuid("3"), uuid("4"))
        }
        repository.deleteFromJobQueue(id1!!)

        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        it("should pick and delete another job") {
            id2 shouldNot beNull()
            id1 shouldNotBe id2
            id2 shouldBeIn listOf(uuid("3"), uuid("4"))
        }
        repository.deleteFromJobQueue(id2!!)

        val id3 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
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

        val id1 = repository.findJob(lockTimeout = Duration.ofDays(1))
        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))

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

            val id1 = repository.findJob(lockTimeout = Duration.ofMillis(1))
            delay(Duration.ofMillis(100).toMillis())
            val abandonedLocks = repository.releaseTimedOutJobLocks()

            abandonedLocks shouldHaveSize 1
            abandonedLocks[0].varselId shouldBe id1

            val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
            id1 shouldBe id2
        }
    }


    describe("release and reacquire lock") {
        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofMinutes(1))!!

        it("should get the job") {
            id1 shouldBe uuid("3")
        }

        it("should be returned to work queue") {
            repository.returnToJobQueue(id1)
        }

        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))!!
        it("should get it again") {
            id2 shouldBe uuid("3")
        }
    }

    describe("read and write of notification") {
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        val id1 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))

        it("har fått to forskjellige id-er") {
            id1 shouldNot beNull()
            id2 shouldNot beNull()
            id1 shouldNotBe id2
        }

        val varsel1 = repository.findVarsel(id1!!)
        val varsel2 = repository.findVarsel(id2!!)


        it("har fått to varsler av forskjellig type") {
            varsel1 shouldNot beNull()
            varsel1 as EksternVarselTilstand

            varsel2 shouldNot beNull()
            varsel2 as EksternVarselTilstand

            val type1 = varsel1.data.eksternVarsel::class
            val type2 = varsel2.data.eksternVarsel::class

            type1 shouldNotBe type2

            type1 shouldBeIn listOf(EksternVarsel.Sms::class, EksternVarsel.Epost::class)
            type2 shouldBeIn listOf(EksternVarsel.Sms::class, EksternVarsel.Epost::class)
        }

    }

    describe("Kan gå gjennom tilstandene ok") {
        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofDays(10))!!
        val varsel1 = repository.findVarsel(id1)!!

        it("should be Ny") {
            varsel1 shouldBe instanceOf(EksternVarselTilstand.Ny::class)
        }

        repository.markerSomSendtAndReleaseJob(
            id1,
            AltinnVarselKlient.AltinnResponse.Ok(
                rå = NullNode.instance
            )
        )

        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))!!
        val varsel2 = repository.findVarsel(id2)

        it("should be utført") {
            varsel2 shouldBe instanceOf(EksternVarselTilstand.Utført::class)
        }

        repository.markerSomKvittertAndDeleteJob(id2)

        val id3 = repository.findJob(lockTimeout = Duration.ofDays(1))

        it("no more work") {
            id3 should beNull()
        }

        val varsel3 = repository.findVarsel(id2)!!
        it("should be completed") {
            varsel3 shouldBe instanceOf(EksternVarselTilstand.Kvittert::class)
            varsel3 as EksternVarselTilstand.Kvittert
            varsel3.response shouldBe instanceOf(AltinnVarselKlient.AltinnResponse.Ok::class)
        }
    }

    describe("Kan gå gjennom tilstandene altinn-feil") {
        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofDays(10))!!
        val varsel1 = repository.findVarsel(id1)!!

        it("should be Ny") {
            varsel1 shouldBe instanceOf(EksternVarselTilstand.Ny::class)
        }

        repository.markerSomSendtAndReleaseJob(
            id1,
            AltinnVarselKlient.AltinnResponse.Feil(
                rå = NullNode.instance,
                feilmelding = "hallo",
                feilkode = "1",
            )
        )

        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))!!
        val varsel2 = repository.findVarsel(id2)

        it("should be utført") {
            varsel2 shouldBe instanceOf(EksternVarselTilstand.Utført::class)
        }

        repository.markerSomKvittertAndDeleteJob(id2)

        val id3 = repository.findJob(lockTimeout = Duration.ofDays(1))

        it("no more work") {
            id3 should beNull()
        }

        val varsel3 = repository.findVarsel(id2)!!
        it("should be failed") {
            varsel3 shouldBe instanceOf(EksternVarselTilstand.Kvittert::class)
            varsel3 as EksternVarselTilstand.Kvittert
            val response = varsel3.response
            response shouldBe instanceOf(AltinnVarselKlient.AltinnResponse.Feil::class)
            response as AltinnVarselKlient.AltinnResponse.Feil
            response.feilkode shouldBe "1"
            response.feilmelding shouldBe "hallo"
        }
    }

    describe("Hard delete event for sak") {
        val hardDelete =
            HendelseModel.HardDelete(
                virksomhetsnummer = "42",
                aggregateId = UUID.randomUUID(),
                hendelseId = UUID.randomUUID(),
                produsentId = "42",
                kildeAppNavn = "test:app",
                deletedAt = OffsetDateTime.now()
            )

        it("oppdater modell etter hendelse feiler ikke") {
            shouldNotThrowAny {
                repository.oppdaterModellEtterHendelse(hardDelete)
            }
        }
    }
})
