package no.nav.arbeidsgiver.notifikasjon.produsent_api

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.produsent.api.Error
import no.nav.arbeidsgiver.notifikasjon.produsent.api.QueryMineNotifikasjoner
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.time.ExperimentalTime

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class MineNotifikasjonerTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(),
            produsentRepository = produsentModel
        )
    )
    val virksomhetsnummer = "123"
    val merkelapp = "tag"
    val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )
    val grupperingsid = "sak1"

    beforeContainer {
        val uuid = UUID.randomUUID()
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = "1",
                mottakere = listOf(mottaker),
                hendelseId = uuid,
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                grupperingsid = grupperingsid,
                kildeAppNavn = "",
                produsentId = "",
                eksterneVarsler = listOf(),
            )
        )
        val uuid2 = UUID.randomUUID()
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.BeskjedOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = "2",
                mottakere = listOf(mottaker),
                hendelseId = uuid2,
                notifikasjonId = uuid2,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                grupperingsid = grupperingsid,
                kildeAppNavn = "",
                produsentId = "",
                eksterneVarsler = listOf(),
            )
        )
        val uuid3 = UUID.randomUUID()
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.BeskjedOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp + "noe annet",
                eksternId = "3",
                mottakere = listOf(mottaker),
                hendelseId = uuid3,
                notifikasjonId = uuid3,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                grupperingsid = grupperingsid,
                produsentId = "",
                kildeAppNavn = "",
                eksterneVarsler = listOf(),
            )
        )
        val uuid4 = UUID.randomUUID()
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.BeskjedOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp + "2",
                eksternId = "3",
                mottakere = listOf(mottaker),
                hendelseId = uuid4,
                notifikasjonId = uuid4,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
                grupperingsid = grupperingsid,
                produsentId = "",
                kildeAppNavn = "",
                eksterneVarsler = listOf(),
            )
        )
    }

    describe("mineNotifikasjoner") {
        context("produsent mangler tilgang til merkelapp") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(merkelapp: "foo") {
                            __typename
                            ... on UgyldigMerkelapp {
                                feilmelding
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                response.getTypedContent<Error.UgyldigMerkelapp>("mineNotifikasjoner")
            }
        }

        context("henter alle med default paginering") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(merkelapp: "tag") {
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
                                        mottakere {
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
                                        }
                                        beskjed {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                      }
                                      ... on Oppgave {
                                        mottakere {
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
                                        }
                                        oppgave {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                            tilstand
                                        }
                                        eksterneVarsler {
                                            id
                                        }
                                      }
                                    }
                                }
                                
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                response.getTypedContent<QueryMineNotifikasjoner.NotifikasjonConnection>("mineNotifikasjoner")
            }
        }

        context("henter alle med angitt paginering") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(merkelapp: "tag", first: 1) {
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
                                        }
                                        beskjed {
                                            __typename
                                            lenke
                                            merkelapp
                                            tekst
                                        }
                                        eksterneVarsler {
                                            id
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
                                        eksterneVarsler {
                                            id
                                        }
                                      }
                                    }
                                }
                                
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val connection = response.getTypedContent<QueryMineNotifikasjoner.NotifikasjonConnection>("mineNotifikasjoner")
                connection.edges.size shouldBe 1
                connection.pageInfo.hasNextPage shouldBe true
            }
        }

        context("når merkelapper=[] i filter") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(merkelapper: []) {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val edges = response.getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                edges.size shouldBe 0
            }
        }

        context("når merkelapp(er) ikke er angitt i filter") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val edges = response.getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                edges.size shouldBe 3
            }
        }

        context("når merkelapper er angitt i filter") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(
                            merkelapper: ["tag"]
                        ) {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val edges = response.getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                edges.size shouldBe 2
            }
        }

        context("når forskjellige merkelapper er angitt i filter") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(
                            merkelapper: ["tag" "tag2"]
                        ) {
                            __typename
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val edges = response.getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                edges.size shouldBe 3
            }
        }

        context("når grupperingsid er angitt i filter") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(merkelapp: "tag", grupperingsid: "$grupperingsid") {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val edges = response.getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                edges.size shouldBe 2
            }
        }

        context("når feil grupperingsid er angitt i filter") {
            val response = engine.produsentApi(
                """
                    query {
                        mineNotifikasjoner(merkelapp: "tag", grupperingsid: "bogus") {
                            ... on NotifikasjonConnection {
                                edges {
                                    cursor
                                }
                            }
                        }
                    }
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val edges = response.getTypedContent<List<JsonNode>>("mineNotifikasjoner/edges")
                edges.size shouldBe 0
            }
        }
    }
})

