package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class MineNotifikasjonerTests : DescribeSpec({
    val database = testDatabase(ProdusentMain.databaseConfig)
    val produsentModel = ProdusentModelImpl(database)
    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(),
            produsentRegister = mockProdusentRegister,
            produsentModelFuture = CompletableFuture.completedFuture(produsentModel)
        )
    )
    val virksomhetsnummer = "123"
    val merkelapp = "tag"
    val mottaker = AltinnMottaker(
        virksomhetsnummer = virksomhetsnummer,
        serviceCode = "1",
        serviceEdition = "1"
    )

    beforeContainer {
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = "1",
                mottaker = mottaker,
                id = UUID.randomUUID(),
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")
            )
        )
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.BeskjedOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = "2",
                mottaker = mottaker,
                id = UUID.randomUUID(),
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")
            )
        )
    }

    describe("mineNotifikasjoner") {
        context("produsent mangler tilgang til merkelapp") {
            val response = engine.produsentApi(
                //language=GraphQL
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
                response.getTypedContent<ProdusentAPI.Error.UgyldigMerkelapp>("mineNotifikasjoner")
            }
        }

        context("henter alle med default paginering") {
            val response = engine.produsentApi(
                //language=GraphQL
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
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                response.getTypedContent<ProdusentAPI.NotifikasjonConnection>("mineNotifikasjoner")
            }
        }

    }
})

