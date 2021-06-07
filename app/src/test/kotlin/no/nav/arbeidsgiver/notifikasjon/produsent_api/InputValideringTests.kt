package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Brreg
import kotlin.time.ExperimentalTime

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class InputValideringTests : DescribeSpec({
    val altinn = object : Altinn {
        override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<QueryModel.Tilgang>()
    }
    val brreg: Brreg = mockk()

    val engine = ktorTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            brreg = brreg,
            queryModelFuture = mockk(),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(),
            produsentRegister = mockProdusentRegister
        )
    )

    describe("input-validering av produsent-api") {
        context("når tekst er over 300 tegn") {
            val response = engine.produsentApi(
                """
                        mutation {
                            nyBeskjed(nyBeskjed: {
                                lenke: "https://foo.bar",
                                tekst: "${"x".repeat(301)}",
                                merkelapp: "tag",
                                eksternId: "heu",
                                mottaker: {
                                    naermesteLeder: {
                                        naermesteLederFnr: "12345678910",
                                        ansattFnr: "3213"
                                        virksomhetsnummer: "42"
                                    } 
                                }
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }) {
                                __typename
                            }
                        }
                    """.trimIndent()
            )
            it("errors har forklarende feilmelding") {
                val errors = response.getGraphqlErrors()
                errors shouldHaveSize 1
                errors.first().message shouldContain "felt 'tekst' overstiger max antall tegn. antall=301, max=300"
            }
        }
    }

    context("Mutation.nyBeskjed med ingen mottaker") {
        val response = engine.produsentApi(
            """
                    mutation {
                        nyBeskjed(nyBeskjed: {
                            lenke: "https://foo.bar",
                            tekst: "hello world",
                            merkelapp: "tag",
                            eksternId: "heu",
                            mottaker: {
                            }
                            opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                        }) {
                            __typename
                        }
                    }
                """.trimIndent()
        )

        it("Feil pga ingen mottaker-felt oppgitt") {
            response.getGraphqlErrors()[0].message shouldContainIgnoringCase "nøyaktig ett felt"
        }
    }

    context("Mutation.nyBeskjed med to mottakere") {
        val response = engine.produsentApi(
            """
                    mutation {
                        nyBeskjed(nyBeskjed: {
                            lenke: "https://foo.bar",
                            tekst: "hello world",
                            merkelapp: "tag",
                            eksternId: "heu",
                            mottaker: {
                                altinn: {
                                    serviceCode: "1234"
                                    serviceEdition: "321"
                                    virksomhetsnummer: "123456789"
                                }
                                naermesteLeder: {
                                    naermesteLederFnr: "00112233344"
                                    ansattFnr: "11223344455"
                                    virksomhetsnummer: "123456789"
                                }
                            }
                            opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                        }) {
                            __typename
                            ... on NyBeskjedVellykket {
                                id
                            }
                        }
                    }
                """.trimIndent()
        )

        it("response inneholder ikke feil") {
            response.getGraphqlErrors()[0].message shouldContainIgnoringCase "nøyaktig ett felt"
        }
    }
})

