package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.util.ProdusentRepositoryStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer

class TilgangsstyringTests : DescribeSpec({
    val engine = ktorProdusentTestServer(
        produsentRepository = ProdusentRepositoryStub()
    )

    describe("tilgangsstyring av produsent-api") {

        context("når produsent mangler tilgang til merkelapp") {
            val merkelapp = "foo-bar"
            val resultat = engine.produsentApi(
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

            it("errors har forklarende feilmelding") {
                resultat should beOfType<Error.UgyldigMerkelapp>()
                resultat as Error.UgyldigMerkelapp
                resultat.feilmelding shouldContain merkelapp
            }
        }

        context("når produsent mangler tilgang til mottaker") {
            val mottaker = AltinnMottaker(
                serviceCode = "1337",
                serviceEdition = "3",
                virksomhetsnummer = "42"
            )

            val resultat = engine.produsentApi(
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

            it("errors har forklarende feilmelding") {
                resultat should beOfType<Error.UgyldigMottaker>()
                resultat as Error.UgyldigMottaker
                resultat.feilmelding shouldContain mottaker.serviceCode
                resultat.feilmelding shouldContain mottaker.serviceEdition
            }
        }
    }
})

