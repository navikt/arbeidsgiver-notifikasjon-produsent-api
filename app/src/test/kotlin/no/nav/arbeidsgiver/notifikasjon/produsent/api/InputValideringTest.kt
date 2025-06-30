package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import no.nav.arbeidsgiver.notifikasjon.util.*
import org.intellij.lang.annotations.Language
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.toString

class InputValideringTest {
    @Test
    fun `mutation#nyBeskjed`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // norsk mobilnummer
        listOf(
            "40000000",
            "004740000000",
            "+4740000000",
            "48999999",
            "004748999999",
            "+4748999999",
            "90000000",
            "004790000000",
            "+4790000000",
            "99999999",
            "004799999999",
            "+4799999999",
        ).forEach { tlf ->
            assertTrue(
                client.nyBeskjed(
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
                ).getGraphqlErrors().isEmpty(),
                "Forventet at $tlf er et gyldig norsk mobil"
            )
        }

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
        ).forEach { tlf ->
            assertEquals(
                "Exception while fetching data (/nyBeskjed) : Kontaktinfo.tlf: verdien er ikke et gyldig norsk mobilnummer.",
                client.nyBeskjed(
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
                ).getGraphqlErrors()[0].message
            )
        }

        listOf(
            "",
        ).forEach { tlf ->
            assertEquals(
                "Exception while fetching data (/nyBeskjed) : Kontaktinfo.tlf: verdien kan ikke være blank.",
                client.nyBeskjed(
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
                ).getGraphqlErrors()[0].message
            )
        }

        // blokkerte serier iht nkom https://stenonicprdnoea01.blob.core.windows.net/enonicpubliccontainer/numsys/nkom.no/E164.csv
        listOf(
            "42000000",
            "004742000000",
            "+4742000000",
            "42999999",
            "004742999999",
            "+4742999999",
            "43000000",
            "004743000000",
            "+4743000000",
            "43999999",
            "004743999999",
            "+4743999999",
            "44000000",
            "004744000000",
            "+4744000000",
            "44999999",
            "004744999999",
            "+4744999999",
            "45360000",
            "004745360000",
            "+4745360000",
            "45369999",
            "004745369999",
            "+4745369999",
            "49000000",
            "004749000000",
            "+4749000000",
            "49999999",
            "004749999999",
            "+4749999999",
        ).forEach { tlf ->
            assertEquals(
                "Exception while fetching data (/nyBeskjed) : Kontaktinfo.tlf: verdien er ikke et gyldig norsk mobilnummer. Nummerserien ${tlf.replace(Regex("""(\+47|0047)"""), "").take(4)}xxxx er blokkert. (se: NKOM E164).",
                client.nyBeskjed(
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
                ).getGraphqlErrors()[0].message
            )
        }

        listOf(
            "donald@duck.co",
        ).forEach { epost ->
            assertTrue(
                client.nyBeskjed(
                    eksterneVarsler = """
                                [
                                    {
                                        "epost": {
                                            "mottaker": {
                                               "kontaktinfo": {
                                                  "epostadresse": "$epost"  
                                               }
                                            },
                                            "epostTittel": "test",
                                            "epostHtmlBody": "test",
                                            "sendetidspunkt": {
                                                "sendevindu": "LOEPENDE"
                                            }
                                        }
                                    }
                                ]
                                """.trimIndent()
                ).getGraphqlErrors().isEmpty()
            )
        }

        listOf(
            "",
        ).forEach { epost ->
            assertEquals(
                "Exception while fetching data (/nyBeskjed) : Kontaktinfo.epostadresse: verdien kan ikke være blank.",
                client.nyBeskjed(
                    eksterneVarsler = """
                                [
                                    {
                                        "epost": {
                                            "mottaker": {
                                               "kontaktinfo": {
                                                  "epostadresse": "$epost"  
                                               }
                                            },
                                            "epostTittel": "test",
                                            "epostHtmlBody": "test",
                                            "sendetidspunkt": {
                                                "sendevindu": "LOEPENDE"
                                            }
                                        }
                                    }
                                ]
                                """.trimIndent()
                ).getGraphqlErrors()[0].message
            )
        }

        listOf(
            "foo",
            "foo@foo",
        ).forEach { epost ->
            assertEquals(
                "Exception while fetching data (/nyBeskjed) : Kontaktinfo.epostadresse: verdien er ikke en gyldig e-postadresse.",
                client.nyBeskjed(
                    eksterneVarsler = """
                                [
                                    {
                                        "epost": {
                                            "mottaker": {
                                               "kontaktinfo": {
                                                  "epostadresse": "$epost"  
                                               }
                                            },
                                            "epostTittel": "test",
                                            "epostHtmlBody": "test",
                                            "sendetidspunkt": {
                                                "sendevindu": "LOEPENDE"
                                            }
                                        }
                                    }
                                ]
                                """.trimIndent()
                ).getGraphqlErrors()[0].message
            )
        }

        // tekst maks lengde
        assertEquals(
            "Exception while fetching data (/nyBeskjed) : notifikasjon.tekst: verdien overstiger maks antall tegn, antall=301, maks=300.",
            client.nyBeskjed(
                tekst = "x".repeat(301)
            ).getGraphqlErrors()[0].message
        )

        // ingen mottaker
        assertEquals(
            "Exception while fetching data (/nyBeskjed) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)",
            client.nyBeskjed(
                mottakere = "[{}]"
            ).getGraphqlErrors()[0].message
        )

        // to mottaker-felt oppgitt på en mottaker
        assertEquals(
            "Exception while fetching data (/nyBeskjed) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)",
            client.nyBeskjed(
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
            ).getGraphqlErrors()[0].message
        )

        // tekst med identifiserende data
        assertEquals(
            "Exception while fetching data (/nyBeskjed) : notifikasjon.tekst: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.nyBeskjed(
                tekst = "1".repeat(11)
            ).getGraphqlErrors()[0].message
        )

        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyBeskjed) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.nyBeskjed(
                hardDelete = """
                            {
                                "den": "2001-12-24T10:44:01",
                                "om": "P2DT3H4M"
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyBeskjed) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.nyBeskjed(
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
            ).getGraphqlErrors()[0].message
        )

        assertEquals(
            "Exception while fetching data (/nyBeskjed) : epost.mottaker: nøyaktig ett felt skal være satt. (Ingen felt er satt)",
            client.nyBeskjed(
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
            ).getGraphqlErrors()[0].message
        )

        // sendetidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyBeskjed) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)",
            client.nyBeskjed(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#nyOppgave`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // tekst maks lengde
        assertEquals(
            "Exception while fetching data (/nyOppgave) : notifikasjon.tekst: verdien overstiger maks antall tegn, antall=301, maks=300.",
            client.nyOppgave(
                tekst = "x".repeat(301),
            ).getGraphqlErrors().first().message
        )

        // ingen mottaker
        assertEquals(
            "Exception while fetching data (/nyOppgave) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)",
            client.nyOppgave(
                mottakere = """[{}]"""
            ).getGraphqlErrors()[0].message
        )

        // to mottaker-felt oppgitt på en mottaker
        assertEquals(
            "Exception while fetching data (/nyOppgave) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)",
            client.nyOppgave(
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
            ).getGraphqlErrors()[0].message
        )

        // tekst med identifiserende data
        assertEquals(
            "Exception while fetching data (/nyOppgave) : notifikasjon.tekst: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.nyOppgave(
                tekst = "1".repeat(11)
            ).getGraphqlErrors()[0].message
        )

        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyOppgave) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.nyOppgave(
                hardDelete = """
                            {
                                "den": "2001-12-24T10:44:01",
                                "om": "P2DT3H4M"
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyOppgave) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.nyOppgave(
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
            ).getGraphqlErrors()[0].message
        )

        assertEquals(
            "Exception while fetching data (/nyOppgave) : epost.mottaker: nøyaktig ett felt skal være satt. (Ingen felt er satt)",
            client.nyOppgave(
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
            ).getGraphqlErrors()[0].message
        )

        // sendetidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyOppgave) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)",
            client.nyOppgave(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyOppgave) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.nyOppgave(
                paaminnelse = """
                            {
                                "tidspunkt": {
                                    "konkret": "2001-12-24T10:44:01",
                                    "etterOpprettelse": "P2DT3H4M"
                                }
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyOppgave) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.nyOppgave(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#nyKalenderavtale`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // tekst maks lengde
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : kalenderavtale.tekst: verdien overstiger maks antall tegn, antall=301, maks=300.",
            client.nyKalenderavtale(
                tekst = "x".repeat(301),
            ).getGraphqlErrors()[0].message
        )

        // ingen mottaker
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)",
            client.nyKalenderavtale(
                mottakere = """[{}]"""
            ).getGraphqlErrors()[0].message
        )

        // to mottaker-felt oppgitt på en mottaker
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)",
            client.nyKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )

        // tekst med identifiserende data
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : kalenderavtale.tekst: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.nyKalenderavtale(
                tekst = "1".repeat(11)
            ).getGraphqlErrors()[0].message
        )

        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.nyKalenderavtale(
                hardDelete = """
                            {
                                "den": "2001-12-24T10:44:01",
                                "om": "P2DT3H4M"
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.nyKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )

        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : epost.mottaker: nøyaktig ett felt skal være satt. (Ingen felt er satt)",
            client.nyKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )

        // sendetidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)",
            client.nyKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.nyKalenderavtale(
                paaminnelse = """
                            {
                                "tidspunkt": {
                                    "konkret": "2001-12-24T10:44:01",
                                    "etterOpprettelse": "P2DT3H4M"
                                }
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nyKalenderavtale) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.nyKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#nySak`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // tittel maks lengde
        assertEquals(
            "Exception while fetching data (/nySak) : sak.tittel: verdien overstiger maks antall tegn, antall=141, maks=140.",
            client.nySak(
                tittel = "A".repeat(141),
            ).getGraphqlErrors()[0].message
        )

        // tittel med identifiserende data
        assertEquals(
            "Exception while fetching data (/nySak) : sak.tittel: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.nySak(
                tittel = "Stor Lampe identifiserende data: 99999999999"
            ).getGraphqlErrors()[0].message
        )

        // tilleggsinformasjon maks lengde
        assertEquals(
            "Exception while fetching data (/nySak) : sak.tilleggsinformasjon: verdien overstiger maks antall tegn, antall=141, maks=140.",
            client.nySak(
                tilleggsinformasjon = "A".repeat(141)
            ).getGraphqlErrors()[0].message
        )

        // tilleggsinformasjon med identifiserende data
        assertEquals(
            "Exception while fetching data (/nySak) : sak.tilleggsinformasjon: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.nySak(
                tilleggsinformasjon = "Stor Lampe identifiserende data: 99999999999"
            ).getGraphqlErrors()[0].message
        )

        // ingen mottaker
        assertEquals(
            "Exception while fetching data (/nySak) : MottakerInput: nøyaktig ett felt skal være satt. (Ingen felt er satt)",
            client.nySak(
                mottakere = """[{}]"""
            ).getGraphqlErrors()[0].message
        )

        // to mottaker-felt oppgitt på en mottaker
        assertEquals(
            "Exception while fetching data (/nySak) : MottakerInput: nøyaktig ett felt skal være satt. (altinn, naermesteLeder er gitt)",
            client.nySak(
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
            ).getGraphqlErrors()[0].message
        )

        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/nySak) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.nySak(
                hardDelete = """
                            {
                                "den": "2001-12-24T10:44:01",
                                "om": "P2DT3H4M"
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#tilleggsinformasjonSak`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // tilleggsinformasjon maks lengde
        assertEquals(
            "Exception while fetching data (/tilleggsinformasjonSak) : tilleggsinformasjon: verdien overstiger maks antall tegn, antall=141, maks=140.",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )

        // tilleggsinformasjon med identifiserende data
        assertEquals(
            "Exception while fetching data (/tilleggsinformasjonSak) : tilleggsinformasjon: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#tilleggsinformasjonSakByGrupperingsid`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // tilleggsinformasjon maks lengde
        assertEquals(
            "Exception while fetching data (/tilleggsinformasjonSakByGrupperingsid) : tilleggsinformasjon: verdien overstiger maks antall tegn, antall=141, maks=140.",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )

        // tilleggsinformasjon med identifiserende data
        assertEquals(
            "Exception while fetching data (/tilleggsinformasjonSakByGrupperingsid) : tilleggsinformasjon: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveUtfoert`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveUtfoert) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveUtfoertByEksternId`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveUtfoertByEksternId) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            @Suppress("GraphQLDeprecatedSymbols")
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )

    }

    @Test
    fun `mutation#oppgaveUtfoertByEksternId_V2`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveUtfoertByEksternId_V2) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveUtgaatt`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        assertEquals(
            "Exception while fetching data (/oppgaveUtgaatt) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveUtgaattByEksternId`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        assertEquals(
            "Exception while fetching data (/oppgaveUtgaattByEksternId) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveUtsettFrist`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveUtsettFrist) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveUtsettFrist) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveUtsettFristByEksternId`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveUtsettFristByEksternId) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveUtsettFristByEksternId) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveEndrePaaminnelse`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveEndrePaaminnelse) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveEndrePaaminnelse) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppgaveEndrePaaminnelseByEksternId`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveEndrePaaminnelseByEksternId) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppgaveEndrePaaminnelseByEksternId) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.produsentApi(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppdaterKalenderavtale`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // tekst maks lengde
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtale) : kalenderavtale.tekst: verdien overstiger maks antall tegn, antall=301, maks=300.",
            client.oppdaterKalenderavtale(
                tekst = "x".repeat(301),
            ).getGraphqlErrors()[0].message
        )

        // tekst med identifiserende data
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtale) : kalenderavtale.tekst: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.oppdaterKalenderavtale(
                tekst = "1".repeat(11)
            ).getGraphqlErrors()[0].message
        )

        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtale) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.oppdaterKalenderavtale(
                hardDelete = """
                            {
                                "nyTid": {
                                    "den": "2001-12-24T10:44:01",
                                    "om": "P2DT3H4M"
                                },
                                "strategi": "OVERSKRIV"
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtale) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.oppdaterKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )

        // sendetidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtale) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)",
            client.oppdaterKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtale) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.oppdaterKalenderavtale(
                paaminnelse = """
                            {
                                "tidspunkt": {
                                    "konkret": "2001-12-24T10:44:01",
                                    "etterOpprettelse": "P2DT3H4M"
                                }
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtale) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.oppdaterKalenderavtale(
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
            ).getGraphqlErrors()[0].message
        )
    }

    @Test
    fun `mutation#oppdaterKalenderavtaleByEksternId`() = ktorProdusentTestServer(
        produsentRepository = object : ProdusentRepositoryStub() {
            val oppgave = EksempelHendelse.OppgaveOpprettet.tilProdusentModel()
            override suspend fun hentNotifikasjon(id: UUID) = oppgave
            override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String) = oppgave
        }
    ) {
        // tekst maks lengde
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : kalenderavtale.tekst: verdien overstiger maks antall tegn, antall=301, maks=300.",
            client.oppdaterKalenderavtaleByEksternId(
                tekst = "x".repeat(301),
            ).getGraphqlErrors()[0].message
        )

        // tekst med identifiserende data
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : kalenderavtale.tekst: verdien inneholder uønsket data: personnummer (11 siffer)",
            client.oppdaterKalenderavtaleByEksternId(
                tekst = "1".repeat(11)
            ).getGraphqlErrors()[0].message
        )

        // harddelete nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : FutureTemporalInput: nøyaktig ett felt skal være satt. (den, om er gitt)",
            client.oppdaterKalenderavtaleByEksternId(
                hardDelete = """
                            {
                                "nyTid": {
                                    "den": "2001-12-24T10:44:01",
                                    "om": "P2DT3H4M"
                                },
                                "strategi": "OVERSKRIV"
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : EksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.oppdaterKalenderavtaleByEksternId(
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
            ).getGraphqlErrors()[0].message
        )

        // sendetidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : SendetidspunktInput: nøyaktig ett felt skal være satt. (tidspunkt, sendevindu er gitt)",
            client.oppdaterKalenderavtaleByEksternId(
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
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.tidspunkt nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : PaaminnelseTidspunktInput: nøyaktig ett felt skal være satt. (konkret, etterOpprettelse er gitt)",
            client.oppdaterKalenderavtaleByEksternId(
                paaminnelse = """
                            {
                                "tidspunkt": {
                                    "konkret": "2001-12-24T10:44:01",
                                    "etterOpprettelse": "P2DT3H4M"
                                }
                            }
                        """.trimIndent()
            ).getGraphqlErrors()[0].message
        )

        // paaminnelse.eksterneVarsler nøyaktig ett felt
        assertEquals(
            "Exception while fetching data (/oppdaterKalenderavtaleByEksternId) : PaaminnelseEksterntVarselInput: nøyaktig ett felt skal være satt. (sms, epost er gitt)",
            client.oppdaterKalenderavtaleByEksternId(
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
            ).getGraphqlErrors()[0].message
        )
    }
}

private suspend fun HttpClient.nySak(
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

private suspend fun HttpClient.nyBeskjed(
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

private suspend fun HttpClient.nyOppgave(
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

private suspend fun HttpClient.nyKalenderavtale(
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

private suspend fun HttpClient.oppdaterKalenderavtale(
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

private suspend fun HttpClient.oppdaterKalenderavtaleByEksternId(
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