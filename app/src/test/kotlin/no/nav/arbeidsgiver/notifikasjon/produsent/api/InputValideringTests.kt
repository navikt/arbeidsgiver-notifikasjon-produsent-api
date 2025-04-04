package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import no.nav.arbeidsgiver.notifikasjon.util.*
import org.intellij.lang.annotations.Language
import java.util.*

class InputValideringTests : DescribeSpec({
    val engine = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    )

    describe("input-validering av produsent-api") {
        context("mutation.nyBeskjed") {
            context("norsk mobilnummer") {
                withData(
                    listOf(
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
                    )
                ) { tlf ->
                    engine.nyBeskjed(
                        eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "$tlf"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]
                        """.trimIndent()
                    ).getGraphqlErrors() shouldHaveSize 0
                }

                withData(
                    listOf(
                        "39999999",
                        "004739999999",
                        "+4739999999",
                        "50000000",
                        "004750000000",
                        "+4750000000",
                        "89999999",
                        "004789999999",
                        "+4789999999",
                    )
                ) { tlf ->
                    engine.nyBeskjed(
                        eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "$tlf"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]
                        """.trimIndent()
                    ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : Kontaktinfo.tlf: verdien er ikke et gyldig norsk mobilnummer."
                }
            }

            it("tekst maks lengde") {
                engine.nyBeskjed(
                    tekst = "x".repeat(301),
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : notifikasjon.tekst: verdien overstiger maks antall tegn, antall=301, maks=300."
            }

            it("ingen mottaker") {
                engine.nyBeskjed(
                    mottakere = """[{}]"""
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)"
            }

            it("to mottaker-felt oppgitt på en mottaker") {
                engine.nyBeskjed(
                    mottakere = """
                        [{
                            "altinn": {
                                "serviceCode": "1234",
                                "serviceEdition": "321"
                            },
                            "naermesteLeder": {
                                "naermesteLederFnr": "00112233344",
                                "ansattFnr": "11223344455"
                            }
                        }]
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)"
            }

            it("tekst med identifiserende data") {
                engine.nyBeskjed(
                    tekst = "1".repeat(11)
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : notifikasjon.tekst: verdien inneholder uønsket data: personnummer (11 siffer)"
            }

            it("harddelete nøyaktig ett felt") {
                engine.nyBeskjed(
                    hardDelete = """
                        {
                            "den": "2001-12-24T10:44:01",
                            "om": "P2DT3H4M"
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }

            it("eksterneVarsler nøyaktig ett felt") {
                engine.nyBeskjed(
                    eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "40000000"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                },
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"

                engine.nyBeskjed(
                    eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "40000000"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                },
                                "epost": {
                                    "mottaker": {
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : epost.mottaker: nøyaktig ett felt skal være satt. (Ingen felt er satt)"
            }

            it("sendetidspunkt nøyaktig ett felt") {
                engine.nyBeskjed(
                    eksterneVarsler = """
                        [
                            {
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE",
                                        "tidspunkt": "2001-12-24T10:44:01"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyBeskjed) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)"
            }
        }

        context("Mutation.nyOppgave") {
            it("tekst maks lengde") {
                engine.nyOppgave(
                    tekst = "x".repeat(301),
                ).getGraphqlErrors().first().message shouldBe "Exception while fetching data (/nyOppgave) : notifikasjon.tekst: verdien overstiger maks antall tegn, antall=301, maks=300."
            }

            it("ingen mottaker") {
                engine.nyOppgave(
                    mottakere = """[{}]"""
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)"
            }

            it("to mottaker-felt oppgitt på en mottaker") {
                engine.nyOppgave(
                    mottakere = """
                        [{
                            "altinn": {
                                "serviceCode": "1234",
                                "serviceEdition": "321"
                            },
                            "naermesteLeder": {
                                "naermesteLederFnr": "00112233344",
                                "ansattFnr": "11223344455"
                            }
                        }]
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)"
            }

            it("tekst med identifiserende data") {
                engine.nyOppgave(
                    tekst = "1".repeat(11)
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : notifikasjon.tekst: verdien inneholder uønsket data: personnummer (11 siffer)"
            }

            it("harddelete nøyaktig ett felt") {
                engine.nyOppgave(
                    hardDelete = """
                        {
                            "den": "2001-12-24T10:44:01",
                            "om": "P2DT3H4M"
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }

            it("eksterneVarsler nøyaktig ett felt") {
                engine.nyOppgave(
                    eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "40000000"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                },
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"

                engine.nyOppgave(
                    eksterneVarsler = """
                        [
                            {
                                "epost": {
                                    "mottaker": {
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : epost.mottaker: nøyaktig ett felt skal være satt. (Ingen felt er satt)"
            }

            it("sendetidspunkt nøyaktig ett felt") {
                engine.nyOppgave(
                    eksterneVarsler = """
                        [
                            {
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE",
                                        "tidspunkt": "2001-12-24T10:44:01"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)"
            }

            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.nyOppgave(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "konkret": "2001-12-24T10:44:01",
                                "etterOpprettelse": "P2DT3H4M"
                            }
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.nyOppgave(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "etterOpprettelse": "P2DT3H4M"
                            },
                            "eksterneVarsler": [
                                {
                                    "sms": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "tlf": "40000000"  
                                           }
                                        },
                                        "smsTekst": "test",
                                        "sendevindu": "LOEPENDE"
                                    },
                                    "epost": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "epostadresse": "foo@bar.baz"
                                           }
                                        },
                                        "epostTittel": "tittel",
                                        "epostHtmlBody": "html",
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            ]
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyOppgave) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }

        context("Mutation.nyKalenderavtale") {
            it("tekst maks lengde") {
                engine.nyKalenderavtale(
                    tekst = "x".repeat(301),
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : kalenderavtale.tekst: verdien overstiger maks antall tegn, antall=301, maks=300."
            }

            it("ingen mottaker") {
                engine.nyKalenderavtale(
                    mottakere = """[{}]"""
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)"
            }

            it("to mottaker-felt oppgitt på en mottaker") {
                engine.nyKalenderavtale(
                    mottakere = """
                        [{
                            "altinn": {
                                "serviceCode": "1234",
                                "serviceEdition": "321"
                            },
                            "naermesteLeder": {
                                "naermesteLederFnr": "00112233344",
                                "ansattFnr": "11223344455"
                            }
                        }]
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)"
            }

            it("tekst med identifiserende data") {
                engine.nyKalenderavtale(
                    tekst = "1".repeat(11)
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : kalenderavtale.tekst: verdien inneholder uønsket data: personnummer (11 siffer)"
            }

            it("harddelete nøyaktig ett felt") {
                engine.nyKalenderavtale(
                    hardDelete = """
                        {
                            "den": "2001-12-24T10:44:01",
                            "om": "P2DT3H4M"
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }

            it("eksterneVarsler nøyaktig ett felt") {
                engine.nyKalenderavtale(
                    eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "40000000"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                },
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"

                engine.nyKalenderavtale(
                    eksterneVarsler = """
                        [
                            {
                                "epost": {
                                    "mottaker": {
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : epost.mottaker: nøyaktig ett felt skal være satt. (Ingen felt er satt)"
            }

            it("sendetidspunkt nøyaktig ett felt") {
                engine.nyKalenderavtale(
                    eksterneVarsler = """
                        [
                            {
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE",
                                        "tidspunkt": "2001-12-24T10:44:01"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)"
            }

            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.nyKalenderavtale(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "konkret": "2001-12-24T10:44:01",
                                "etterOpprettelse": "P2DT3H4M"
                            }
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.nyKalenderavtale(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "etterOpprettelse": "P2DT3H4M"
                            },
                            "eksterneVarsler": [
                                {
                                    "sms": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "tlf": "40000000"  
                                           }
                                        },
                                        "smsTekst": "test",
                                        "sendevindu": "LOEPENDE"
                                    },
                                    "epost": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "epostadresse": "foo@bar.baz"
                                           }
                                        },
                                        "epostTittel": "tittel",
                                        "epostHtmlBody": "html",
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            ]
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nyKalenderavtale) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }

        context("Mutation.nySak") {
            it("tittel maks lengde") {
                engine.nySak(
                    tittel = "A".repeat(141),
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nySak) : sak.tittel: verdien overstiger maks antall tegn, antall=141, maks=140."
            }

            it("tittel med identifiserende data") {
                engine.nySak(
                    tittel = "Stor Lampe identifiserende data: 99999999999"
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nySak) : sak.tittel: verdien inneholder uønsket data: personnummer (11 siffer)"
            }

            it("tilleggsinformasjon maks lengde") {
                engine.nySak(
                    tilleggsinformasjon = "A".repeat(141)
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nySak) : sak.tilleggsinformasjon: verdien overstiger maks antall tegn, antall=141, maks=140."
            }

            it("tilleggsinformasjon med identifiserende data") {
                engine.nySak(
                    tilleggsinformasjon = "Stor Lampe identifiserende data: 99999999999"
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nySak) : sak.tilleggsinformasjon: verdien inneholder uønsket data: personnummer (11 siffer)"
            }

            it("ingen mottaker") {
                engine.nySak(
                    mottakere = """[{}]"""
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nySak) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)"
            }

            it("to mottaker-felt oppgitt på en mottaker") {
                engine.nySak(
                    mottakere = """
                        [{
                            "altinn": {
                                "serviceCode": "1234",
                                "serviceEdition": "321"
                            },
                            "naermesteLeder": {
                                "naermesteLederFnr": "00112233344",
                                "ansattFnr": "11223344455"
                            }
                        }]
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nySak) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)"
            }

            it("harddelete nøyaktig ett felt") {
                engine.nySak(
                    hardDelete = """
                        {
                            "den": "2001-12-24T10:44:01",
                            "om": "P2DT3H4M"
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/nySak) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }
        }

        context("Mutation.tilleggsinformasjonSak") {
            it("tilleggsinformasjon maks lengde") {
                engine.produsentApi(
                    """
                    mutation {
                        tilleggsinformasjonSak(
                            id: "${uuid("42")}"
                            tilleggsinformasjon: "${"A".repeat(141)}"
                        ) {
                            __typename
                            ... on TilleggsinformasjonSakVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/tilleggsinformasjonSak) : tilleggsinformasjon: verdien overstiger maks antall tegn, antall=141, maks=140."
            }

            it("tilleggsinformasjon med identifiserende data") {
                engine.produsentApi(
                    """
                    mutation {
                        tilleggsinformasjonSak(
                            id: "${uuid("42")}"
                            tilleggsinformasjon: "Stor Lampe identifiserende data: 99999999999"
                        ) {
                            __typename
                            ... on TilleggsinformasjonSakVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/tilleggsinformasjonSak) : tilleggsinformasjon: verdien inneholder uønsket data: personnummer (11 siffer)"
            }
        }

        context("Mutation.tilleggsinformasjonSakByGrupperingsid") {
            it("tilleggsinformasjon maks lengde") {
                engine.produsentApi(
                    """
                    mutation {
                        tilleggsinformasjonSakByGrupperingsid(
                            grupperingsid: "42"
                            merkelapp: "tag"
                            tilleggsinformasjon: "${"A".repeat(141)}"
                        ) {
                            __typename
                            ... on TilleggsinformasjonSakVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/tilleggsinformasjonSakByGrupperingsid) : tilleggsinformasjon: verdien overstiger maks antall tegn, antall=141, maks=140."
            }

            it("tilleggsinformasjon med identifiserende data") {
                engine.produsentApi(
                    """
                    mutation {
                        tilleggsinformasjonSakByGrupperingsid(
                            grupperingsid: "42"
                            merkelapp: "tag"
                            tilleggsinformasjon: "Stor Lampe identifiserende data: 99999999999"
                        ) {
                            __typename
                            ... on TilleggsinformasjonSakVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/tilleggsinformasjonSakByGrupperingsid) : tilleggsinformasjon: verdien inneholder uønsket data: personnummer (11 siffer)"
            }
        }

        context("Mutation.oppgaveUtfoert") {
            it("harddelete nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveUtfoert(
                            id: "${uuid("42")}"
                            hardDelete: {
                                nyTid: {
                                    den: "2001-12-24T10:44:01",
                                    om: "P2DT3H4M"
                                },
                                strategi: OVERSKRIV
                            }
                        ) {
                            __typename
                            ... on OppgaveUtfoertVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtfoert) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }
        }

        context("Mutation.oppgaveUtfoertByEksternId") {
            it("harddelete nøyaktig ett felt") {
                @Suppress("GraphQLDeprecatedSymbols")
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveUtfoertByEksternId(
                            merkelapp: "tag"
                            eksternId: "42"
                            hardDelete: {
                                nyTid: {
                                    den: "2001-12-24T10:44:01",
                                    om: "P2DT3H4M"
                                },
                                strategi: OVERSKRIV
                            }
                        ) {
                            __typename
                            ... on OppgaveUtfoertVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtfoertByEksternId) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }
        }

        context("Mutation.oppgaveUtfoertByEksternId_V2") {
            it("harddelete nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveUtfoertByEksternId_V2(
                            merkelapp: "tag"
                            eksternId: "42"
                            hardDelete: {
                                nyTid: {
                                    den: "2001-12-24T10:44:01",
                                    om: "P2DT3H4M"
                                },
                                strategi: OVERSKRIV
                            }
                        ) {
                            __typename
                            ... on OppgaveUtfoertVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtfoertByEksternId_V2) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }
        }

        context("Mutation.oppgaveUtgaatt") {
            engine.produsentApi(
                """
                mutation {
                    oppgaveUtgaatt(
                        id: "${uuid("42")}"
                        hardDelete: {
                            nyTid: {
                                den: "2001-12-24T10:44:01",
                                om: "P2DT3H4M"
                            },
                            strategi: OVERSKRIV
                        }
                    ) {
                        __typename
                        ... on OppgaveUtgaattVellykket {
                            id
                        }
                    }
                }""".trimIndent()
            ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtgaatt) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
        }

        context("Mutation.oppgaveUtgaattByEksternId") {
            engine.produsentApi(
                """
                mutation {
                    oppgaveUtgaattByEksternId(
                        merkelapp: "tag"
                        eksternId: "42"
                        hardDelete: {
                            nyTid: {
                                den: "2001-12-24T10:44:01",
                                om: "P2DT3H4M"
                            },
                            strategi: OVERSKRIV
                        }
                    ) {
                        __typename
                        ... on OppgaveUtgaattVellykket {
                            id
                        }
                    }
                }""".trimIndent()
            ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtgaattByEksternId) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
        }

        context("Mutation.oppgaveUtsettFrist") {
            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveUtsettFrist(
                            id: "${uuid("42")}"
                            nyFrist: "2020-01-02"
                            paaminnelse: {
                                tidspunkt: {
                                    konkret: "2001-12-24T10:44:01",
                                    etterOpprettelse: "P2DT3H4M"
                                }
                            }
                        ) {
                            __typename
                            ... on OppgaveUtsettFristVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtsettFrist) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveUtsettFrist(
                            id: "${uuid("42")}"
                            nyFrist: "2020-01-02"
                            paaminnelse: {
                                tidspunkt: {
                                    etterOpprettelse: "P2DT3H4M"
                                },
                                eksterneVarsler: [
                                {
                                    sms: {
                                        mottaker: {
                                           kontaktinfo: {
                                              tlf: "40000000"  
                                           }
                                        },
                                        smsTekst: "test",
                                        sendevindu: LOEPENDE
                                    },
                                    epost: {
                                        mottaker: {
                                           kontaktinfo: {
                                              epostadresse: "foo@bar.baz"
                                           }
                                        },
                                        epostTittel: "tittel",
                                        epostHtmlBody: "html",
                                        sendevindu: LOEPENDE
                                    }
                                }
                            ]
                          }
                        ) {
                            __typename
                            ... on OppgaveUtsettFristVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtsettFrist) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }

        context("Mutation.oppgaveUtsettFristByEksternId") {
            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveUtsettFristByEksternId(
                            eksternId: "42"
                            merkelapp: "tag"
                            nyFrist: "2020-01-02"
                            paaminnelse: {
                                tidspunkt: {
                                    konkret: "2001-12-24T10:44:01",
                                    etterOpprettelse: "P2DT3H4M"
                                }
                            }
                        ) {
                            __typename
                            ... on OppgaveUtsettFristVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtsettFristByEksternId) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveUtsettFristByEksternId(
                            eksternId: "42"
                            merkelapp: "tag"
                            nyFrist: "2020-01-02"
                            paaminnelse: {
                                tidspunkt: {
                                    etterOpprettelse: "P2DT3H4M"
                                },
                                eksterneVarsler: [
                                {
                                    sms: {
                                        mottaker: {
                                           kontaktinfo: {
                                              tlf: "40000000"  
                                           }
                                        },
                                        smsTekst: "test",
                                        sendevindu: LOEPENDE
                                    },
                                    epost: {
                                        mottaker: {
                                           kontaktinfo: {
                                              epostadresse: "foo@bar.baz"
                                           }
                                        },
                                        epostTittel: "tittel",
                                        epostHtmlBody: "html",
                                        sendevindu: LOEPENDE
                                    }
                                }
                            ]
                          }
                        ) {
                            __typename
                            ... on OppgaveUtsettFristVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveUtsettFristByEksternId) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }

        context("Mutation.oppgaveEndrePaaminnelse") {
            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "${uuid("42")}"
                            paaminnelse: {
                                tidspunkt: {
                                    konkret: "2001-12-24T10:44:01",
                                    etterOpprettelse: "P2DT3H4M"
                                }
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveEndrePaaminnelse) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelse(
                            id: "${uuid("42")}"
                            paaminnelse: {
                                tidspunkt: {
                                    etterOpprettelse: "P2DT3H4M"
                                },
                                eksterneVarsler: [
                                {
                                    sms: {
                                        mottaker: {
                                           kontaktinfo: {
                                              tlf: "40000000"  
                                           }
                                        },
                                        smsTekst: "test",
                                        sendevindu: LOEPENDE
                                    },
                                    epost: {
                                        mottaker: {
                                           kontaktinfo: {
                                              epostadresse: "foo@bar.baz"
                                           }
                                        },
                                        epostTittel: "tittel",
                                        epostHtmlBody: "html",
                                        sendevindu: LOEPENDE
                                    }
                                }
                            ]
                          }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveEndrePaaminnelse) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }

        context("Mutation.oppgaveEndrePaaminnelseByEksternId") {
            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelseByEksternId(
                            eksternId: "42"
                            merkelapp: "tag"
                            paaminnelse: {
                                tidspunkt: {
                                    konkret: "2001-12-24T10:44:01",
                                    etterOpprettelse: "P2DT3H4M"
                                }
                            }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveEndrePaaminnelseByEksternId) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.produsentApi(
                    """
                    mutation {
                        oppgaveEndrePaaminnelseByEksternId(
                            eksternId: "42"
                            merkelapp: "tag"
                            paaminnelse: {
                                tidspunkt: {
                                    etterOpprettelse: "P2DT3H4M"
                                },
                                eksterneVarsler: [
                                {
                                    sms: {
                                        mottaker: {
                                           kontaktinfo: {
                                              tlf: "40000000"  
                                           }
                                        },
                                        smsTekst: "test",
                                        sendevindu: LOEPENDE
                                    },
                                    epost: {
                                        mottaker: {
                                           kontaktinfo: {
                                              epostadresse: "foo@bar.baz"
                                           }
                                        },
                                        epostTittel: "tittel",
                                        epostHtmlBody: "html",
                                        sendevindu: LOEPENDE
                                    }
                                }
                            ]
                          }
                        ) {
                            __typename
                            ... on OppgaveEndrePaaminnelseVellykket {
                                id
                            }
                        }
                    }""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppgaveEndrePaaminnelseByEksternId) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }

        context("Mutation.oppdaterKalenderavtale") {
            it("tekst maks lengde") {
                engine.oppdaterKalenderavtale(
                    tekst = "x".repeat(301),
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtale) : kalenderavtale.tekst: verdien overstiger maks antall tegn, antall=301, maks=300."
            }

            it("tekst med identifiserende data") {
                engine.oppdaterKalenderavtale(
                    tekst = "1".repeat(11)
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtale) : kalenderavtale.tekst: verdien inneholder uønsket data: personnummer (11 siffer)"
            }

            it("harddelete nøyaktig ett felt") {
                engine.oppdaterKalenderavtale(
                    hardDelete = """
                        {
                            "nyTid": {
                                "den": "2001-12-24T10:44:01",
                                "om": "P2DT3H4M"
                            },
                            "strategi": "OVERSKRIV"
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe  "Exception while fetching data (/oppdaterKalenderavtale) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }

            it("eksterneVarsler nøyaktig ett felt") {
                engine.oppdaterKalenderavtale(
                    eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "40000000"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                },
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtale) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }

            it("sendetidspunkt nøyaktig ett felt") {
                engine.oppdaterKalenderavtale(
                    eksterneVarsler = """
                        [
                            {
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE",
                                        "tidspunkt": "2001-12-24T10:44:01"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtale) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)"
            }

            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.oppdaterKalenderavtale(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "konkret": "2001-12-24T10:44:01",
                                "etterOpprettelse": "P2DT3H4M"
                            }
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtale) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.oppdaterKalenderavtale(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "etterOpprettelse": "P2DT3H4M"
                            },
                            "eksterneVarsler": [
                                {
                                    "sms": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "tlf": "40000000"  
                                           }
                                        },
                                        "smsTekst": "test",
                                        "sendevindu": "LOEPENDE"
                                    },
                                    "epost": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "epostadresse": "foo@bar.baz"
                                           }
                                        },
                                        "epostTittel": "tittel",
                                        "epostHtmlBody": "html",
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            ]
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtale) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }

        context("Mutation.oppdaterKalenderavtaleByEksternId") {
            it("tekst maks lengde") {
                engine.oppdaterKalenderavtaleByEksternId(
                    tekst = "x".repeat(301),
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : kalenderavtale.tekst: verdien overstiger maks antall tegn, antall=301, maks=300."
            }

            it("tekst med identifiserende data") {
                engine.oppdaterKalenderavtaleByEksternId(
                    tekst = "1".repeat(11)
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : kalenderavtale.tekst: verdien inneholder uønsket data: personnummer (11 siffer)"
            }

            it("harddelete nøyaktig ett felt") {
                engine.oppdaterKalenderavtaleByEksternId(
                    hardDelete = """
                        {
                            "nyTid": {
                                "den": "2001-12-24T10:44:01",
                                "om": "P2DT3H4M"
                            },
                            "strategi": "OVERSKRIV"
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)"
            }

            it("eksterneVarsler nøyaktig ett felt") {
                engine.oppdaterKalenderavtaleByEksternId(
                    eksterneVarsler = """
                        [
                            {
                                "sms": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "tlf": "40000000"  
                                       }
                                    },
                                    "smsTekst": "test",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                },
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }

            it("sendetidspunkt nøyaktig ett felt") {
                engine.oppdaterKalenderavtaleByEksternId(
                    eksterneVarsler = """
                        [
                            {
                                "epost": {
                                    "mottaker": {
                                       "kontaktinfo": {
                                          "epostadresse": "foo@bar.baz"
                                       }
                                    },
                                    "epostTittel": "tittel",
                                    "epostHtmlBody": "html",
                                    "sendetidspunkt": {
                                        "sendevindu": "LOEPENDE",
                                        "tidspunkt": "2001-12-24T10:44:01"
                                    }
                                }
                            }
                        ]""".trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)"
            }

            it("paaminnelse.tidspunkt nøyaktig ett felt") {
                engine.oppdaterKalenderavtaleByEksternId(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "konkret": "2001-12-24T10:44:01",
                                "etterOpprettelse": "P2DT3H4M"
                            }
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)"
            }

            it("paaminnelse.eksterneVarsler nøyaktig ett felt") {
                engine.oppdaterKalenderavtaleByEksternId(
                    paaminnelse = """
                        {
                            "tidspunkt": {
                                "etterOpprettelse": "P2DT3H4M"
                            },
                            "eksterneVarsler": [
                                {
                                    "sms": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "tlf": "40000000"  
                                           }
                                        },
                                        "smsTekst": "test",
                                        "sendevindu": "LOEPENDE"
                                    },
                                    "epost": {
                                        "mottaker": {
                                           "kontaktinfo": {
                                              "epostadresse": "foo@bar.baz"
                                           }
                                        },
                                        "epostTittel": "tittel",
                                        "epostHtmlBody": "html",
                                        "sendevindu": "LOEPENDE"
                                    }
                                }
                            ]
                        }
                    """.trimIndent()
                ).getGraphqlErrors()[0].message shouldBe "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)"
            }
        }
    }
})

private fun TestApplicationEngine.nySak(
    tittel: String = "tittel",
    tilleggsinformasjon: String = "her er noe tilleggsinformasjon",
    @Language("JSON") mottakere: String = """
        [{
            "altinn": {
                "serviceCode": "5441",
                "serviceEdition": "1"
            }
        }]
    """.trimIndent(),
    @Language("JSON") hardDelete: String? = null,
) = produsentApi(
    GraphQLRequest(
        query = """
            mutation NySak( 
                ${'$'}tittel: String!
                ${'$'}tilleggsinformasjon: String!
                ${'$'}mottakere: [MottakerInput!]!
                ${'$'}hardDelete: FutureTemporalInput
            ) {
                nySak(
                    virksomhetsnummer: "1"
                    merkelapp: "tag"
                    grupperingsid: "gr1"
                    mottakere: ${'$'}mottakere
                    tilleggsinformasjon: ${'$'}tilleggsinformasjon
                    initiellStatus: MOTTATT
                    tidspunkt: "2020-01-01T01:01Z"
                    tittel: ${'$'}tittel
                    lenke: "#foo"
                    hardDelete: ${'$'}hardDelete
                ) {
                    __typename
                    ... on NySakVellykket {
                        id
                    }
                }
            }
        """,
        variables = mapOf(
            "tittel" to tittel,
            "tilleggsinformasjon" to tilleggsinformasjon,
            "mottakere" to mottakere.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            },
            "hardDelete" to hardDelete?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            },
        ),
    )
)

private fun TestApplicationEngine.nyBeskjed(
    tekst: String = "tittel",
    @Language("JSON") mottakere: String = """
        [{
            "altinn": {
                "serviceCode": "5441",
                "serviceEdition": "1"
            }
        }]
    """.trimIndent(),
    @Language("JSON") hardDelete: String? = null,
    @Language("JSON") eksterneVarsler: String? = null,
) = produsentApi(
    GraphQLRequest(
        query = """
            mutation NyBeskjed( 
                ${'$'}tekst: String! 
                ${'$'}mottakere: [MottakerInput!]!
                ${'$'}hardDelete: FutureTemporalInput
                ${'$'}eksterneVarsler: [EksterntVarselInput!]!
            ) {
                nyBeskjed(
                    nyBeskjed: {
                        mottakere: ${'$'}mottakere
                        notifikasjon: {
                            merkelapp: "tag"
                            tekst: ${'$'}tekst
                            lenke: "#foo"
                        }
                        metadata: {
                            virksomhetsnummer: "1"
                            eksternId: "42"
                            opprettetTidspunkt: "2011-12-03T10:15:30+01:00"
                            grupperingsid: "gr1"
                            hardDelete: ${'$'}hardDelete
                        }
                        eksterneVarsler: ${'$'}eksterneVarsler
                    }
                ) {
                    __typename
                    ... on NyBeskjedVellykket {
                        id
                    }
                }
            }
        """,
        variables = mapOf(
            "tekst" to tekst,
            "mottakere" to mottakere.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            },
            "hardDelete" to hardDelete?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            },
            "eksterneVarsler" to (eksterneVarsler?.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            } ?: emptyList()),
        ),
    )
)

private fun TestApplicationEngine.nyOppgave(
    tekst: String = "tittel",
    @Language("JSON") mottakere: String = """
        [{
            "altinn": {
                "serviceCode": "5441",
                "serviceEdition": "1"
            }
        }]
    """.trimIndent(),
    @Language("JSON") hardDelete: String? = null,
    @Language("JSON") eksterneVarsler: String? = null,
    @Language("JSON") paaminnelse: String? = null,
) = produsentApi(
    GraphQLRequest(
        query = """
            mutation NyOppgave( 
                ${'$'}tekst: String! 
                ${'$'}mottakere: [MottakerInput!]!
                ${'$'}hardDelete: FutureTemporalInput
                ${'$'}eksterneVarsler: [EksterntVarselInput!]!
                ${'$'}paaminnelse: PaaminnelseInput
            )  {
                nyOppgave(
                    nyOppgave: {
                        mottakere: ${'$'}mottakere
                        notifikasjon: {
                            merkelapp: "tag"
                            tekst: ${'$'}tekst
                            lenke: "#foo"
                        }
                        metadata: {
                            virksomhetsnummer: "1"
                            eksternId: "42"
                            opprettetTidspunkt: "2011-12-03T10:15:30+01:00"
                            grupperingsid: "gr1"
                            hardDelete: ${'$'}hardDelete
                        }
                        eksterneVarsler: ${'$'}eksterneVarsler
                        paaminnelse: ${'$'}paaminnelse
                    }
                ) {
                    __typename
                    ... on NyOppgaveVellykket {
                        id
                    }
                }
            }
        """,
        variables = mapOf(
            "tekst" to tekst,
            "mottakere" to mottakere.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            },
            "hardDelete" to hardDelete?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            },
            "eksterneVarsler" to (eksterneVarsler?.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            } ?: emptyList()),
            "paaminnelse" to paaminnelse?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            }
        ),
    )
)

private fun TestApplicationEngine.nyKalenderavtale(
    tekst: String = "tekst",
    @Language("JSON") mottakere: String = """
        [{
            "altinn": {
                "serviceCode": "5441",
                "serviceEdition": "1"
            }
        }]
    """.trimIndent(),
    @Language("JSON") hardDelete: String? = null,
    @Language("JSON") eksterneVarsler: String? = null,
    @Language("JSON") paaminnelse: String? = null,
) = produsentApi(
    GraphQLRequest(
        query = """
            mutation NyKalenderavtale( 
                ${'$'}tekst: String! 
                ${'$'}mottakere: [MottakerInput!]!
                ${'$'}hardDelete: FutureTemporalInput
                ${'$'}eksterneVarsler: [EksterntVarselInput!]!
                ${'$'}paaminnelse: PaaminnelseInput
            )  {
                nyKalenderavtale(
                    virksomhetsnummer: "1"
                    grupperingsid: "gr1"
                    merkelapp: "tag"
                    eksternId: "42"
                    tekst: ${'$'}tekst
                    lenke: "#foo"
                    mottakere: ${'$'}mottakere
                    startTidspunkt: "2011-12-03T10:15:30"
                    eksterneVarsler: ${'$'}eksterneVarsler
                    paaminnelse: ${'$'}paaminnelse
                    hardDelete: ${'$'}hardDelete
                ) {
                    __typename
                    ... on NyKalenderavtaleVellykket {
                        id
                    }
                }
            }
        """,
        variables = mapOf(
            "tekst" to tekst,
            "mottakere" to mottakere.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            },
            "hardDelete" to hardDelete?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            },
            "eksterneVarsler" to (eksterneVarsler?.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            } ?: emptyList()),
            "paaminnelse" to paaminnelse?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            }
        ),
    )
)

private fun TestApplicationEngine.oppdaterKalenderavtale(
    tekst: String = "tekst",
    @Language("JSON") hardDelete: String? = null,
    @Language("JSON") eksterneVarsler: String? = null,
    @Language("JSON") paaminnelse: String? = null,
) = produsentApi(
    GraphQLRequest(
        query = """
            mutation OppdaterKalenderavtale( 
                ${'$'}tekst: String! 
                ${'$'}hardDelete: HardDeleteUpdateInput
                ${'$'}eksterneVarsler: [EksterntVarselInput!]!
                ${'$'}paaminnelse: PaaminnelseInput
            ) {
                oppdaterKalenderavtale(
                    id: "${uuid("42")}" 
                    nyTekst: ${'$'}tekst
                    eksterneVarsler: ${'$'}eksterneVarsler
                    paaminnelse: ${'$'}paaminnelse
                    hardDelete: ${'$'}hardDelete
                ) {
                    __typename
                    ... on OppdaterKalenderavtaleVellykket {
                        id
                    }
                }
            }
        """,
        variables = mapOf(
            "tekst" to tekst,
            "hardDelete" to hardDelete?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            },
            "eksterneVarsler" to (eksterneVarsler?.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            } ?: emptyList()),
            "paaminnelse" to paaminnelse?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            }
        ),
    )
)

private fun TestApplicationEngine.oppdaterKalenderavtaleByEksternId(
    tekst: String = "tekst",
    @Language("JSON") hardDelete: String? = null,
    @Language("JSON") eksterneVarsler: String? = null,
    @Language("JSON") paaminnelse: String? = null,
) = produsentApi(
    GraphQLRequest(
        query = """
            mutation OppdaterKalenderavtale( 
                ${'$'}tekst: String! 
                ${'$'}hardDelete: HardDeleteUpdateInput
                ${'$'}eksterneVarsler: [EksterntVarselInput!]!
                ${'$'}paaminnelse: PaaminnelseInput
            ) {
                oppdaterKalenderavtaleByEksternId(
                    merkelapp: "tag" 
                    eksternId: "42" 
                    nyTekst: ${'$'}tekst
                    eksterneVarsler: ${'$'}eksterneVarsler
                    paaminnelse: ${'$'}paaminnelse
                    hardDelete: ${'$'}hardDelete
                ) {
                    __typename
                    ... on OppdaterKalenderavtaleVellykket {
                        id
                    }
                }
            }
        """,
        variables = mapOf(
            "tekst" to tekst,
            "hardDelete" to hardDelete?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            },
            "eksterneVarsler" to (eksterneVarsler?.let {
                laxObjectMapper.readValue<List<Map<String, Any?>>>(it)
            } ?: emptyList()),
            "paaminnelse" to paaminnelse?.let {
                laxObjectMapper.readValue<Map<String, Any?>>(it)
            }
        ),
    )
)