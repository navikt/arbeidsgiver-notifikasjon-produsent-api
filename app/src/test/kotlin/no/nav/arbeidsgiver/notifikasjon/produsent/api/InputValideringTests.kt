package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer

class InputValideringTests : DescribeSpec({
    val engine = ktorProdusentTestServer()

    describe("input-validering av produsent-api") {
        context("når tekst er over 300 tegn") {
            val msg = "x".repeat(301)
            val response = engine.produsentApi(
                """
                        mutation {
                            nyBeskjed(nyBeskjed: {
                                mottaker: {
                                    naermesteLeder: {
                                        naermesteLederFnr: "12345678910",
                                        ansattFnr: "3213"
                                    } 
                                }
                                notifikasjon: {
                                    lenke: "https://foo.bar"
                                    tekst: "$msg"
                                    merkelapp: "tag"
                                }
                                metadata: {
                                    eksternId: "heu"
                                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                    virksomhetsnummer: "42"
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
                errors.first().message shouldContain "maks antall tegn"
                errors.first().message shouldContain "antall=301, maks=300"
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
                                virksomhetsnummer: "42"
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
                                }
                                naermesteLeder: {
                                    naermesteLederFnr: "00112233344"
                                    ansattFnr: "11223344455"
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
                                virksomhetsnummer: "123456789"
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
            val fnr = "1".repeat(11)
            val response = engine.produsentApi(
                """
                    mutation {
                        nyBeskjed(nyBeskjed: {
                            notifikasjon: {
                                lenke: "https://foo.bar",
                                tekst: "$fnr",
                                merkelapp: "tag",
                            }
                            mottaker: {
                                naermesteLeder: {
                                    naermesteLederFnr: "12345678910",
                                    ansattFnr: "3213"
                                } 
                            }
                            metadata: {
                                eksternId: "heuer",
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                virksomhetsnummer: "42"
                            }
                        }) {
                            __typename
                        }
                    }
                """.trimIndent()
            )

            it("feil pga identifiserende data i tekst") {
                response.getGraphqlErrors()[0].message shouldContainIgnoringCase "personnummer"
            }
        }

        context("når ekstern varsling er mot telefonnummer") {
            withData(listOf(
                "40000000",
                "004740000000",
                "+4740000000",
                "49999999",
                "004749999999",
                "+4749999999",
                "90000000",
                "004790000000",
                "+4790000000",
                "99999999",
                "004799999999",
                "+4799999999",
            )) { tlf ->
                engine.produsentApi(
                    """
                        mutation {
                            nyBeskjed(nyBeskjed: {
                                notifikasjon: {
                                    lenke: "https://foo.bar",
                                    tekst: "teste teste",
                                    merkelapp: "tag",
                                }
                                mottaker: {
                                    naermesteLeder: {
                                        naermesteLederFnr: "12345678910",
                                        ansattFnr: "3213"
                                    } 
                                }
                                metadata: {
                                    eksternId: "heuer",
                                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                    virksomhetsnummer: "42"
                                }
                                eksterneVarsler: [
                                    {
                                        sms: {
                                            mottaker: {
                                               kontaktinfo: {
                                                  tlf: "$tlf"  
                                               }
                                            }
                                            smsTekst: "test"
                                            sendetidspunkt: {
                                                sendevindu: LOEPENDE
                                            }
                                        }
                                    }
                                ]
                            }) {
                                __typename
                            }
                        }
                    """
                ).let {
                    it.getGraphqlErrors() shouldHaveSize 0
                }
            }

            withData(listOf(
                "39999999",
                "004739999999",
                "+4739999999",
                "50000000",
                "004750000000",
                "+4750000000",
                "89999999",
                "004789999999",
                "+4789999999",
            )) { tlf ->
                engine.produsentApi(
                    """
                        mutation {
                            nyBeskjed(nyBeskjed: {
                                notifikasjon: {
                                    lenke: "https://foo.bar",
                                    tekst: "teste teste",
                                    merkelapp: "tag",
                                }
                                mottaker: {
                                    naermesteLeder: {
                                        naermesteLederFnr: "12345678910",
                                        ansattFnr: "3213"
                                    } 
                                }
                                metadata: {
                                    eksternId: "heuer",
                                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                    virksomhetsnummer: "42"
                                }
                                eksterneVarsler: [
                                    {
                                        sms: {
                                            mottaker: {
                                               kontaktinfo: {
                                                  tlf: "$tlf"  
                                               }
                                            }
                                            smsTekst: "test"
                                            sendetidspunkt: {
                                                sendevindu: LOEPENDE
                                            }
                                        }
                                    }
                                ]
                            }) {
                                __typename
                            }
                        }
                    """
                ).let {
                    it.getGraphqlErrors()[0].message shouldContainIgnoringCase "ikke et gyldig norsk mobilnummer"
                }
            }


        }
    }
})

