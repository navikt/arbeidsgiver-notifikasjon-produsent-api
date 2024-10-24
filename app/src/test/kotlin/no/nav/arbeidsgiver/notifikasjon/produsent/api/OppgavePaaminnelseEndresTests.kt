package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
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

            val response = engine.produsentApi("""
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
                """)

            it("returnerer tilbake oppgave id-en") {
                val vellykket =
                    response.getTypedContent<MutationOppgavePaaminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka med korrekt påminnelse") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.OppgavePaaminnelseEndret>()
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
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(konkretPaaminnelsesTidspunkt, konkretPaaminnelsesTidspunkt.inOsloAsInstant()),
                    eksterneVarsler = listOf()
                ),
                sakId = null,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }

            val response = engine.produsentApi("""
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
                """)

            it("returnerer tilbake oppgave id-en") {
                val vellykket =
                    response.getTypedContent<MutationOppgavePaaminnelse.OppgaveEndrePaaminnelseVellykket>("oppgaveEndrePaaminnelse")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka med tom påminnelse") {
                val hendelse = stubbedKafkaProducer.hendelser
                    .filterIsInstance<HendelseModel.OppgavePaaminnelseEndret>()
                    .last()
                hendelse.påminnelse shouldBe null
            }
        }

        context("Oppgave finnes ikke") {
            val (_, stubbedKafkaProducer, engine) = setupEngine()
            val response = engine.produsentApi("""
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
                """)

            it("returnerer oppgave finnes ikke") {
                val finnesIkke = response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveEndrePaaminnelse")
                finnesIkke.feilmelding shouldNotBe null
            }

            it("melding er ikke sendt på kafka") {
                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePaaminnelseEndret>() shouldBe emptyList()
            }
        }

        context("Påminnelsestidspunkt er ugyldig") {
            val (_, stubbedKafkaProducer, engine) = setupEngine()
            val response = engine.produsentApi("""
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
                """)

            it("returnerer oppgave finnes ikke") {
                val finnesIkke = response.getTypedContent<Error.NotifikasjonFinnesIkke>("oppgaveEndrePaaminnelse")
                finnesIkke.feilmelding shouldNotBe null
            }

            it("melding er ikke sendt på kafka") {
                stubbedKafkaProducer.hendelser.filterIsInstance<HendelseModel.OppgavePaaminnelseEndret>() shouldBe emptyList()
            }
        }
        //TODO: flere tester?
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