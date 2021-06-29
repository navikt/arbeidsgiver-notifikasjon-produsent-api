package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.ProdusentMain
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.produsent.*
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*


class SoftDeleteNotifikasjonTests : DescribeSpec({
    val database = testDatabase(ProdusentMain.databaseConfig)
    val produsentModel = ProdusentModelImpl(database)
    val kafkaProducer = mockk<CoroutineProducer<KafkaKey, Hendelse>>()

    mockkStatic(CoroutineProducer<KafkaKey, Hendelse>::softDelete)
    coEvery { any<CoroutineProducer<KafkaKey, Hendelse>>().softDelete(any()) } returns Unit

    afterSpec {
        unmockkAll()
    }

    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = kafkaProducer,
            produsentRegister = mockProdusentRegister,
            produsentModel = produsentModel
        )
    )


    describe("SoftDelete-oppførsel") {
        val virksomhetsnummer = "123"
        val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
        val merkelapp = "tag"
        val eksternId = "123"
        val mottaker = AltinnMottaker(
            virksomhetsnummer = virksomhetsnummer,
            serviceCode = "1",
            serviceEdition = "1"
        )
        val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

        context("Eksisterende oppgave blir utført") {
            val oppgaveOpprettet = Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottaker = mottaker,
                id = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = opprettetTidspunkt
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on SoftDeleteNotifikasjonVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer tilbake id-en") {
                val vellykket =
                    response.getTypedContent<ProdusentAPI.SoftDeleteNotifikasjonVellykket>("softDeleteNotifikasjon")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineProducer<KafkaKey, Hendelse>>().softDelete(any())
                }
            }

            it("har slettet-status i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)!!
                notifikasjon.deletedAt shouldNotBe null
            }


            it("mineNotifikasjoner rapporterer som softDeleted") {
                val response = engine.produsentApi("""
                        query {
                            mineNotifikasjoner(merkelapp: "$merkelapp") {
                            __typename
                            ... on NotifikasjonConnection {
                                __typename
                                pageInfo {
                                    hasNextPage
                                    endCursor
                                }
                                edges {
                                    cursor
                                    node {
                                      __typename
                                      ... on Beskjed {
                                        mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid
                                            softDeleted
                                            softDeletedAt
                                        }
                                        beskjed {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                        }
                                      }
                                      ... on Oppgave {
                                      mottaker {
                                            __typename
                                            ... on AltinnMottaker {
                                                serviceCode
                                                serviceEdition
                                                virksomhetsnummer
                                            }
                                            ... on NaermesteLederMottaker {
                                                ansattFnr
                                                naermesteLederFnr
                                                virksomhetsnummer
                                            }
                                        }
                                        metadata {
                                            __typename
                                            id
                                            eksternId
                                            grupperingsid
                                            softDeleted
                                            softDeletedAt
                                        }
                                        oppgave {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                            tilstand
                                        }
                                      }
                                    }
                                }
                            }
                        }
                    }
                    """
                )
               val slettetNotifikasjon = response.getTypedContent<ProdusentAPI.NotifikasjonConnection>("mineNotifikasjoner")
                    .edges
                    .map { it.node }
                    .find { it.id == uuid } !!

                slettetNotifikasjon.metadata.softDeleted shouldBe true
                slettetNotifikasjon.metadata.softDeletedAt shouldNotBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjon")
            }
        }

        context("Oppgave med feil merkelapp") {
            val oppgaveOpprettet = Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = "feil merkelapp",
                eksternId = eksternId,
                mottaker = mottaker,
                id = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = opprettetTidspunkt
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.UgyldigMerkelapp>("softDeleteNotifikasjon")
            }
        }
    }

    describe("softDeleteNotifikasjonByEksternId-oppførsel") {
        val virksomhetsnummer = "123"
        val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
        val merkelapp = "tag"
        val eksternId = "123"
        val mottaker = AltinnMottaker(
            virksomhetsnummer = virksomhetsnummer,
            serviceCode = "1",
            serviceEdition = "1"
        )
        val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

        context("Eksisterende oppgave blir utført") {
            val oppgaveOpprettet = Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottaker = mottaker,
                id = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = opprettetTidspunkt
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on SoftDeleteNotifikasjonVellykket {
                            id
                        }
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer tilbake id-en") {
                val vellykket = response.getTypedContent<ProdusentAPI.SoftDeleteNotifikasjonVellykket>("softDeleteNotifikasjonByEksternId")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineProducer<KafkaKey, Hendelse>>().softDelete(any())
                }
            }

            it("har utført-status i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)!!
                notifikasjon.deletedAt shouldNotBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil merkelapp men riktig eksternId") {
            val oppgaveOpprettet = Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottaker = mottaker,
                id = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = opprettetTidspunkt
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil eksternId men riktig merkelapp") {
            val oppgaveOpprettet = Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottaker = mottaker,
                id = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = opprettetTidspunkt
            )

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    softDeleteNotifikasjonByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("softDeleteNotifikasjonByEksternId")
            }
        }
    }
})
