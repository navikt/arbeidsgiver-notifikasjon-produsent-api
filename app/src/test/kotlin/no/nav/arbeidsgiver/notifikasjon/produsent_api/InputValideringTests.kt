package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.BrregStub
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.ktorTestServer
import kotlin.time.ExperimentalTime

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class InputValideringTests : DescribeSpec({
    val engine = ktorTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = AltinnStub(),
            brreg = BrregStub(),
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
                                mottaker: {
                                    naermesteLeder: {
                                        naermesteLederFnr: "12345678910",
                                        ansattFnr: "3213"
                                        virksomhetsnummer: "42"
                                    } 
                                }
                                notifikasjon: {
                                    lenke: "https://foo.bar"
                                    tekst: "${"x".repeat(301)}"
                                    merkelapp: "tag"
                                }
                                metadata: {
                                    eksternId: "heu"
                                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                }
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

        context("Mutation.nyBeskjed med ingen mottaker") {
            val response = engine.produsentApi(
                """
                    mutation {
                        nyBeskjed(nyBeskjed: {
                            mottaker: {
                            }
                            notifikasjon: {
                                lenke: "https://foo.bar"
                                tekst: "hello world"
                                merkelapp: "tag"
                            }
                            metadata: {
                                eksternId: "heu"
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }
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
                            notifikasjon: {
                                lenke: "https://foo.bar"
                                tekst: "hello world"
                                merkelapp: "tag"
                            }
                            metadata: {
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                eksternId: "heu"
                            }
                        }) {
                            __typename
                            ... on NyBeskjedVellykket {
                                id
                            }
                        }
                    }
                """.trimIndent()
            )

            it("Feil pga to mottaker-felt oppgitt") {
                response.getGraphqlErrors()[0].message shouldContainIgnoringCase "nøyaktig ett felt"
            }
        }

        context("når tekst inneholder fødselsnummer") {
            val response = engine.produsentApi(
                """
                    mutation {
                        nyBeskjed(nyBeskjed: {
                            notifikasjon: {
                                lenke: "https://foo.bar",
                                tekst: "${"1".repeat(11)}",
                                merkelapp: "tag",
                            }
                            mottaker: {
                                naermesteLeder: {
                                    naermesteLederFnr: "12345678910",
                                    ansattFnr: "3213"
                                    virksomhetsnummer: "42"
                                } 
                            }
                            metadata: {
                                eksternId: "heuer",
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }
                        }) {
                            __typename
                        }
                    }
                """.trimIndent()
            )

            it("feil pga identifiserende data i tekst") {
                response.getGraphqlErrors()[0].message shouldContainIgnoringCase "'tekst' kan ikke inneholde identifiserende data"
            }
        }
    }
})

