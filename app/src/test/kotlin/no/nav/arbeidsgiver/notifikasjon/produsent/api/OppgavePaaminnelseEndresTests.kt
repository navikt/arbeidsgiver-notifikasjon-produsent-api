package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class OppgavePaaminnelseEndresTests : DescribeSpec({

    describe("oppgavePåminnelseEndres-oppførsel") {
        val virksomhetsnummer = "123"
        val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
        val merkelapp = "tag"
        val eksternId = "123"
        val mottaker = AltinnMottaker(
            virksomhetsnummer = virksomhetsnummer,
            serviceCode = "1",
            serviceEdition = "1"
        )

        val oppgaveOpprettetTidspunkt = OffsetDateTime.now()
        val konkretPaaminnelsesTidspunkt = oppgaveOpprettetTidspunkt.toLocalDateTime().plusWeeks(1)

        context("Oppgave har ingen påminnelse men får ny") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = uuid,
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid",
                        paaminnelse: {
                            eksterneVarsler:[],
                            tidspunkt: {konkret: "$konkretPaaminnelsesTidspunkt"}
                        }
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer tilbake oppgave id-en") {
                val vellykket =
                    response.getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka med korrekt påminnelse") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.OppgavePåminnelseEndret>()
                    .last()
                hendelse.påminnelse?.tidspunkt shouldBe HendelseModel.PåminnelseTidspunkt.Konkret(
                    konkretPaaminnelsesTidspunkt,
                    konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                )
            }
        }

        context("Oppgave får fjernet påminnelse") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = uuid,
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid", 
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer tilbake oppgave id-en") {
                val vellykket =
                    response.getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka med tom påminnelse") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.OppgavePåminnelseEndret>()
                    .last()
                hendelse.påminnelse shouldBe null
            }
        }

        context("Oppgave finnes ikke") {
            val (_, stubbedKafkaProducer, engine) = setupEngine()
            val response = engine.produsentApi(
                """
                mutation {
                    oppgaveEndrePaaminnelse(
                        id: "$uuid", 
                    ) {
                        __typename
                        ... on OppgaveEndrePaaminnelseVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """
            )

            it("returnerer oppgave finnes ikke") {
                val finnesIkke = response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveEndrePaaminnelse")
                finnesIkke.feilmelding shouldNotBe null
            }

            it("melding er ikke sendt på kafka") {
                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>() shouldBe emptyList()
            }
        }

        context("Påminnelsestidspunkt er før oppgaven er opprettet") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }


            it("Påminnelsestidspunkt er før oppgaveOpprettetTidspunkt") {
                val response = engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {konkret: "${oppgaveOpprettetTidspunkt.toLocalDateTime().minusDays(1)}"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
                )
                val ugyldigPåminnelseTidspunkt =
                    response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
                ugyldigPåminnelseTidspunkt.feilmelding shouldNotBe null
            }


            it("melding er ikke sendt på kafka") {
                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>() shouldBe emptyList()
            }
        }

        context("Påminnelsestidspunkt er relativ til frist, men oppgaven har ingen frist") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            it("Påminnelsestidspunkt er relativ til frist, men oppgave har ingen frist") {
                val response = engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {foerFrist: "P2DT3H4M"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
                )
                val ugyldigPåminnelseTidspunkt =
                    response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
                ugyldigPåminnelseTidspunkt.feilmelding shouldNotBe null
            }

            it("melding er ikke sendt på kafka") {
                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>() shouldBe emptyList()
            }
        }

        context("Oppgaven har en frist, men konkret påminnelsestidspunkt er etter frist") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            val oppgaveFrist = oppgaveOpprettetTidspunkt.plusWeeks(1)

            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = oppgaveFrist.toLocalDate(),
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            it("Påminnelsestidspunkt er etter oppgavens frist") {
                val response = engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {konkret: "${oppgaveFrist.plusDays(1).toLocalDateTime()}"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
                )
                val ugyldigPåminnelseTidspunkt =
                    response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
                ugyldigPåminnelseTidspunkt.feilmelding shouldNotBe null
            }

            it("melding er ikke sendt på kafka") {
                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>() shouldBe emptyList()
            }
        }

        context("Relativ til starttidspunkt") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            val oppgaveFrist = oppgaveOpprettetTidspunkt.plusWeeks(1)

            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = oppgaveFrist.toLocalDate(),
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            it("Påminnelsestidspunkt er relativ til starttidspunkt, men notifikasjon er en oppgave (ikke kalenderavtale)") {
                val response = engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {foerStartTidspunkt: "P2DT3H4M"}
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
                )
                val ugyldigPåminnelseTidspunkt =
                    response.getTypedContent<Error.UgyldigPåminnelseTidspunkt>("oppgaveEndrePaaminnelse")
                ugyldigPåminnelseTidspunkt.feilmelding shouldNotBe null
            }

            it("melding er ikke sendt på kafka") {
                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>() shouldBe emptyList()
            }
        }

        context("Identisk Idepotency Key") {
            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            val oppgaveFrist = oppgaveOpprettetTidspunkt.plusWeeks(1)

            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = UUID.randomUUID(),
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = oppgaveOpprettetTidspunkt,
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = oppgaveFrist.toLocalDate(),
                påminnelse = HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                        konkretPaaminnelsesTidspunkt,
                        konkretPaaminnelsesTidspunkt.inOsloAsInstant()
                    ),
                    eksterneVarsler = listOf()
                ),
                sakId = null,

                ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            it("Mutation med identisk idepotency key kalles to ganger, men kun en melding sendes på kafka") {
                fun kallOppgaveEndrePaaminnelse(): TestApplicationResponse {
                    return engine.produsentApi(
                        """
                        mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                eksterneVarsler:[],
                                tidspunkt: {konkret: "$konkretPaaminnelsesTidspunkt"}
                            }
                            idempotencyKey: "1234"
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
                    )
                }

                val vellykket1 =
                    kallOppgaveEndrePaaminnelse().getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>(
                        "oppgaveEndrePaaminnelse"
                    )
                vellykket1.id shouldBe uuid

                val vellykket2 =
                    kallOppgaveEndrePaaminnelse().getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>(
                        "oppgaveEndrePaaminnelse"
                    )
                vellykket2.id shouldBe uuid

                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePåminnelseEndret>()
                    .count() shouldBe 1
            }
        }
        context("Påminnelse blir endret relativ til op") {

            val (produsentModel, stubbedKafkaProducer, engine) = setupEngine()
            OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOf(mottaker),
                hendelseId = uuid,
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2024-01-01T01:01:00Z"),
                kildeAppNavn = "",
                produsentId = "",
                grupperingsid = null,
                eksterneVarsler = listOf(),
                hardDelete = null,
                frist = null,
                påminnelse = null,
                sakId = null
            ).also { produsentModel.oppdaterModellEtterHendelse(it) }

            val response = engine.produsentApi(
                """
                        mutation {
                        oppgaveEndrePaaminnelse(
                            id: "$uuid",
                            paaminnelse: {
                                tidspunkt: {
                                    etterOpprettelse: "P14DT"
                                }
                            }
                            idempotencyKey: "1234"
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                            ... on Error {
                                feilmelding
                            }
                        }
                    }
                    """
            )

            it("Påminnelsestidspunkt er satt relativ til opprettelse av oppgave") {
                val vellykket =
                    response.getTypedContent<MutationOppgavePåminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
                vellykket.id shouldBe uuid
            }
            it("har sendt melding til kafka") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.OppgavePåminnelseEndret>()
                    .last()
                hendelse.påminnelse?.tidspunkt?.påminnelseTidspunkt shouldBe OffsetDateTime.parse("2024-01-15T01:01:00Z").toInstant()
            }
        }
    }
})

private fun DescribeSpec.setupEngine(): Triple<ProdusentRepositoryImpl, FakeHendelseProdusent, TestApplicationEngine> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val kafkaProducer = FakeHendelseProdusent()
    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentModel
    )
    return Triple(produsentModel, kafkaProducer, engine)
}