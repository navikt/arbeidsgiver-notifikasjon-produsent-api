package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import kotlinx.coroutines.delay
import no.altinn.services.common.fault._2009._10.AltinnFault
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.getUuid
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import javax.xml.bind.JAXBElement
import javax.xml.namespace.QName

class EksternVarslingRepositoryTests: DescribeSpec({

    val oppgaveOpprettet = OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "tag",
        grupperingsid = "42",
        eksternId = "1",
        mottakere = listOf(AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )),
        tekst = "1",
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
            ),
            AltinntjenesteVarselKontaktinfo(
                varselId = uuid("5"),
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1",
                tittel = "hey",
                innhold = "body",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            )
        ),
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )

    describe("Getting and deleting jobs") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        val id1 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        it("should pick and delete a job") {
            id1 shouldNot beNull()
            id1 shouldBeIn listOf(uuid("3"), uuid("4"), uuid("5"))
            repository.findVarsel(id1!!) shouldNot beNull()
        }
        repository.deleteFromJobQueue(id1!!)

        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        it("should pick and delete another job") {
            id2 shouldNot beNull()
            id1 shouldNotBe id2
            id2 shouldBeIn listOf(uuid("3"), uuid("4"), uuid("5"))
            repository.findVarsel(id2!!) shouldNot beNull()
        }
        repository.deleteFromJobQueue(id2!!)

        val id3 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        it("should pick and delete last job") {
            id3 shouldNot beNull()
            id3 shouldNotBe id1
            id3 shouldNotBe id2
            id3 shouldBeIn listOf(uuid("3"), uuid("4"), uuid("5"))
            repository.findVarsel(id3!!) shouldNot beNull()
        }
        repository.deleteFromJobQueue(id3!!)

        val id4 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        it("should be no more jobs to pick") {
            id4 should beNull()
        }
    }

    describe("Can't pick a job while locked") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

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
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

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
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

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
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

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
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

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
            AltinnVarselKlientResponse.Ok(
                rå = NullNode.instance
            )
        )

        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))!!
        val varsel2 = repository.findVarsel(id2)

        it("should be utført") {
            varsel2 shouldBe instanceOf(EksternVarselTilstand.Sendt::class)
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
            varsel3.response shouldBe instanceOf(AltinnResponse.Ok::class)
        }
    }

    describe("Kan gå gjennom tilstandene altinn-feil") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

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
            AltinnVarselKlientResponse.Feil(
                rå = NullNode.instance,
                altinnFault = AltinnFault()
                    .withErrorID(1)
                    .withAltinnErrorMessage(JAXBElement(QName(""), String::class.java, "hallo"))
            )
        )

        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))!!
        val varsel2 = repository.findVarsel(id2)

        it("should be utført") {
            varsel2 shouldBe instanceOf(EksternVarselTilstand.Sendt::class)
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
            response shouldBe instanceOf(AltinnResponse.Feil::class)
            response as AltinnResponse.Feil
            response.feilkode shouldBe "1"
            response.feilmelding shouldBe "hallo"
        }
    }

    describe("Hard delete event for sak") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        val sakId = uuid("442")
        val hardDelete =
            HendelseModel.HardDelete(
                virksomhetsnummer = "42",
                aggregateId = sakId,
                hendelseId = sakId,
                produsentId = "42",
                kildeAppNavn = "test:app",
                deletedAt = OffsetDateTime.now(),
                grupperingsid = oppgaveOpprettet.grupperingsid,
                merkelapp = oppgaveOpprettet.merkelapp,
            )

        it("oppdater modell etter hendelse feiler ikke") {
            shouldNotThrowAny {
                repository.oppdaterModellEtterHendelse(hardDelete)
            }
        }
        it("registrerer hard delete for oppgaven tilknyttet saken hard delete gjelder for") {
            database.nonTransactionalExecuteQuery(
                """
                select * from hard_delete where notifikasjon_id = ?
                """,
                setup = {
                    uuid(oppgaveOpprettet.notifikasjonId)
                },
                transform = {
                    getUuid("notifikasjon_id")
                }
            ) shouldContainExactly listOf(oppgaveOpprettet.notifikasjonId)
        }
        it("fjerner info fra lookup tabell") {
            database.nonTransactionalExecuteQuery(
                """
                select * from merkelapp_grupperingsid_notifikasjon where notifikasjon_id = ?
                """,
                {
                    uuid(oppgaveOpprettet.notifikasjonId)
                }
            ) { asMap() } should beEmpty()
        }
    }

    describe("Oppgave med påminnelse fører ikke til varsel nå") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        val varselId = UUID.randomUUID()
        OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = UUID.randomUUID(),
            hendelseId = UUID.randomUUID(),
            produsentId = "fager",
            kildeAppNavn = "local:local:local",
            merkelapp = "merkelapp",
            eksternId = "ekstern-id",
            mottakere = listOf(AltinnMottaker(
                serviceCode = "",
                serviceEdition = "",
                virksomhetsnummer = "",
            )),
            tekst = "tekst",
            grupperingsid = null,
            lenke = "#",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+01"),
            eksterneVarsler = listOf(),
            hardDelete = null,
            frist = null,
            påminnelse = HendelseModel.Påminnelse(
                tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                    LocalDateTime.parse("2020-01-01T01:01"),
                    Instant.parse("2020-01-01T01:01:01.00Z")
                ),
                eksterneVarsler = listOf(
                    SmsVarselKontaktinfo(
                        varselId = varselId,
                        tlfnr = "1234",
                        fnrEllerOrgnr = "32123",
                        smsTekst = "tekst",
                        sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                        sendeTidspunkt = null,
                    )
                )
            ),
            sakId = null,
        ).also {
            repository.oppdaterModellEtterHendelse(it)
        }

        it("ikke registret") {
            repository.findVarsel(varselId) shouldBe null
        }
    }

    describe("varsler i påminnelse blir registrert") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        val varselId = UUID.randomUUID()
        val notifikasjonId = UUID.randomUUID()
        HendelseModel.PåminnelseOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = notifikasjonId,
            hendelseId = UUID.randomUUID(),
            produsentId = "fager",
            kildeAppNavn = "local:local:local",
            opprettetTidpunkt = Instant.parse("2020-01-01T01:01:01.00Z"),
            fristOpprettetTidspunkt = Instant.parse("2020-01-01T01:01:01.00Z"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = varselId,
                    tlfnr = "1234",
                    fnrEllerOrgnr = "32123",
                    smsTekst = "tekst",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null,
                )
            ),
            frist = null,
            tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                LocalDateTime.parse("2020-01-01T01:01"),
                Instant.parse("2020-01-01T01:01:01.00Z")
            ),
            bestillingHendelseId = notifikasjonId,
        ).also {
            repository.oppdaterModellEtterHendelse(it)
        }

        it("varsel registrert") {
            repository.findVarsel(varselId) shouldBe EksternVarselTilstand.Ny(
                data = EksternVarselStatiskData(
                    varselId = varselId,
                    notifikasjonId = notifikasjonId,
                    produsentId = "fager",
                    eksternVarsel = EksternVarsel.Sms(
                        fnrEllerOrgnr = "32123",
                        sendeVindu = EksterntVarselSendingsvindu.LØPENDE,
                        sendeTidspunkt = null,
                        mobilnummer = "1234",
                        tekst = "tekst",
                    ),
                )
            )
        }
    }

    describe("Hard delete event for oppgave") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        val eksterntVarselVellykket = HendelseModel.EksterntVarselVellykket(
            virksomhetsnummer = "42",
            notifikasjonId = oppgaveOpprettet.aggregateId,
            hendelseId = UUID.randomUUID(),
            produsentId = "42",
            kildeAppNavn = "test:app",
            varselId = oppgaveOpprettet.eksterneVarsler[0].varselId,
            råRespons = NullNode.instance
        )
        val hardDelete = HendelseModel.HardDelete(
            virksomhetsnummer = "42",
            aggregateId = oppgaveOpprettet.aggregateId,
            hendelseId = UUID.randomUUID(),
            produsentId = "42",
            kildeAppNavn = "test:app",
            deletedAt = OffsetDateTime.now(),
            grupperingsid = null,
            merkelapp = oppgaveOpprettet.merkelapp,
        )

        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        repository.oppdaterModellEtterHendelse(eksterntVarselVellykket)
        repository.oppdaterModellEtterHendelse(hardDelete)
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        repository.oppdaterModellEtterHendelse(eksterntVarselVellykket)
        repository.oppdaterModellEtterHendelse(hardDelete)

        it("findVarsel feiler ikke") {
            repository.findVarsel(eksterntVarselVellykket.varselId) shouldNot beNull()
        }
    }

    describe("sendetidspunkt med localdatetime.min") {
        val database = testDatabase(EksternVarsling.databaseConfig)
        val repository = EksternVarslingRepository(database)

        OppgaveOpprettet(
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
                    sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                    sendeTidspunkt = LocalDateTime.parse("-999999999-01-01T00:00")
                ),
            ),
            hardDelete = null,
            frist = null,
            påminnelse = null,
            sakId = null,
        ).also {
            repository.oppdaterModellEtterHendelse(it)
        }

        it("job og varsel blir sendt fortløpende") {
            val id = repository.findJob(lockTimeout = Duration.ofMinutes(1))
            id shouldNot beNull()
            val varsel = repository.findVarsel(id!!)
            varsel shouldNot beNull()
            varsel as EksternVarselTilstand
            varsel.data.eksternVarsel.sendeTidspunkt shouldBe LocalDateTime.parse("-999999999-01-01T00:00")
        }

    }

})

private fun ResultSet.asMap() = (1..this.metaData.columnCount).associate {
    this.metaData.getColumnName(it) to this.getObject(it)
}