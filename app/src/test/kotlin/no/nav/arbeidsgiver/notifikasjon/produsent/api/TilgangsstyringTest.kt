package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.util.ProdusentRepositoryStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import kotlin.test.Test
import kotlin.test.assertTrue

class TilgangsstyringTest {
    @Test
    fun `når produsent mangler tilgang til merkelapp`() = ktorProdusentTestServer(
        produsentRepository = ProdusentRepositoryStub()
    ) {
        val merkelapp = "foo-bar"
        val resultat = client.produsentApi(
            """
                        mutation {
                            nyBeskjed(nyBeskjed: {
                                mottaker: {
                                    naermesteLeder: {
                                        naermesteLederFnr: "12345678910"
                                        ansattFnr: "321234"
                                    } 
                                }
                                notifikasjon: {
                                    lenke: "https://foo.bar"
                                    tekst: "hello world"
                                    merkelapp: "$merkelapp"
                                }
                                metadata: {
                                    eksternId: "heu"
                                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                    virksomhetsnummer: "42"

                                }
                            }) {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
        ).getTypedContent<MutationNyBeskjed.NyBeskjedResultat>("nyBeskjed")

        // errors har forklarende feilmelding
        resultat as Error.UgyldigMerkelapp
        assertTrue(resultat.feilmelding.contains(merkelapp))
    }

    @Test
    fun `når produsent mangler tilgang til mottaker`() = ktorProdusentTestServer(
        produsentRepository = ProdusentRepositoryStub()
    ) {
        val mottaker = AltinnMottaker(
            serviceCode = "1337",
            serviceEdition = "3",
            virksomhetsnummer = "42"
        )

        val resultat = client.produsentApi(
            """
                        mutation {
                            nyBeskjed(nyBeskjed: {
                                mottaker: {
                                    altinn: {
                                        serviceCode: "${mottaker.serviceCode}",
                                        serviceEdition: "${mottaker.serviceEdition}"
                                    } 
                                }
                                notifikasjon: {
                                    lenke: "https://foo.bar"
                                    tekst: "hello world"
                                    merkelapp: "tag"
                                }
                                metadata: {
                                    eksternId: "heu"
                                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                                    virksomhetsnummer: "${mottaker.virksomhetsnummer}"

                                }
                            }) {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
        ).getTypedContent<MutationNyBeskjed.NyBeskjedResultat>("nyBeskjed")

        // errors har forklarende feilmelding
        resultat as Error.UgyldigMottaker
        assertTrue(resultat.feilmelding.contains(mottaker.serviceCode))
        assertTrue(resultat.feilmelding.contains(mottaker.serviceEdition))
    }
}

