package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
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

    beforeContainer {
        val uuid = UUID.randomUUID()
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.OppgaveOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = "1",
                mottaker = mottaker,
                hendelseId = uuid,
                notifikasjonId = uuid,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")
            )
        )
        val uuid2 = UUID.randomUUID()
        produsentModel.oppdaterModellEtterHendelse(
            Hendelse.BeskjedOpprettet(
                virksomhetsnummer = "1",
                merkelapp = merkelapp,
                eksternId = "2",
                mottaker = mottaker,
                hendelseId = uuid2,
                notifikasjonId = uuid2,
                tekst = "test",
                lenke = "https://nav.no",
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z")
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
                response.getTypedContent<ProdusentAPI.Error.UgyldigMerkelapp>("mineNotifikasjoner")
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
                """.trimIndent()
            )

            it("respons inneholder forventet data") {
                val connection = response.getTypedContent<ProdusentAPI.NotifikasjonConnection>("mineNotifikasjoner")
                connection.edges.size shouldBe 1
                connection.pageInfo.hasNextPage shouldBe true
            }
        }
    }
})

