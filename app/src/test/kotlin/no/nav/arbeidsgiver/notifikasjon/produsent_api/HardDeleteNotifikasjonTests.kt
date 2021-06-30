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

// Legg til en test for sletting av én av flere rader i database

class HardDeleteNotifikasjonTests : DescribeSpec({
    val database = testDatabase(ProdusentMain.databaseConfig)
    val produsentModel = ProdusentModelImpl(database)
    val kafkaProducer = mockk<CoroutineProducer<KafkaKey, Hendelse>>()

    mockkStatic(CoroutineProducer<KafkaKey, Hendelse>::hardDelete)
    coEvery { any<CoroutineProducer<KafkaKey, Hendelse>>().hardDelete(any()) } returns Unit

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

    val virksomhetsnummer = "123"
    val uuid = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972cd")
    val uuid2 = UUID.fromString("9d3e3360-1955-4955-bc22-88ccca3972ca")
    val merkelapp = "tag"
    val eksternId = "123"
    val eksternId2 = "234"
    val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    val opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")

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
    val oppgaveOpprettet2 = oppgaveOpprettet.copy(eksternId = eksternId2, id = uuid2)

    describe("HardDelete-oppførsel") {

        context("Eksisterende oppgave blir slettet") {

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on HardDeleteNotifikasjonVellykket {
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
                    response.getTypedContent<ProdusentAPI.HardDeleteNotifikasjonVellykket>("hardDeleteNotifikasjon")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineProducer<KafkaKey, Hendelse>>().hardDelete(any())
                }
            }

            it("har blitt fjernet fra modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)
                notifikasjon shouldBe null
            }
            it("notifikasjon2 har ikke blitt fjernet fra modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid2)
                notifikasjon shouldNotBe null
            }

            it("mineNotifikasjoner rapporterer at notifikasjon ikke finnes") {
                val response = engine.produsentApi(
                    """
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
                val slettetNotifikasjon =
                    response.getTypedContent<ProdusentAPI.NotifikasjonConnection>("mineNotifikasjoner")
                        .edges
                        .map { it.node }
                        .find { it.id == uuid }

                slettetNotifikasjon shouldBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjon")
            }
        }

        context("Oppgave med feil merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(merkelapp = "feil merkelapp"))

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjon(id: "$uuid") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.UgyldigMerkelapp>("hardDeleteNotifikasjon")
            }
        }
    }

    describe("hardDeleteNotifikasjonByEksternId-oppførsel") {


        context("Eksisterende oppgave blir slettet") {

            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet2)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on HardDeleteNotifikasjonVellykket {
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
                    response.getTypedContent<ProdusentAPI.HardDeleteNotifikasjonVellykket>("hardDeleteNotifikasjonByEksternId")
                vellykket.id shouldBe uuid
            }

            it("har sendt melding til kafka") {
                coVerify {
                    any<CoroutineProducer<KafkaKey, Hendelse>>().hardDelete(any())
                }
            }

            it("finnes ikke i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid)
                notifikasjon shouldBe null
            }

            it("oppgave2 finnes fortsatt i modellen") {
                val notifikasjon = produsentModel.hentNotifikasjon(uuid2)
                notifikasjon shouldNotBe null
            }
        }

        context("Oppgave mangler") {
            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil merkelapp men riktig eksternId") {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "$eksternId", merkelapp: "nope$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
            }
        }

        context("Oppgave med feil eksternId men riktig merkelapp") {
            produsentModel.oppdaterModellEtterHendelse(oppgaveOpprettet)

            val response = engine.produsentApi(
                """
                mutation {
                    hardDeleteNotifikasjonByEksternId(eksternId: "nope$eksternId", merkelapp: "$merkelapp") {
                        __typename
                        ... on Error {
                            feilmelding
                        }
                    }
                }
                """.trimIndent()
            )

            it("returnerer feilmelding") {
                response.getTypedContent<ProdusentAPI.Error.NotifikasjonFinnesIkke>("hardDeleteNotifikasjonByEksternId")
            }
        }
    }
})