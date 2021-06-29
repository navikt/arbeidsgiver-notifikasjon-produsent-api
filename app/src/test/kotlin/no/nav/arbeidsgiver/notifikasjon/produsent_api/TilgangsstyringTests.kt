package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import kotlin.time.ExperimentalTime

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class TilgangsstyringTests : DescribeSpec({
    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(),
            produsentRegister = mockProdusentRegister,
            produsentModel = mockk()
        )
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
                                        virksomhetsnummer: "42"
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
                                }
                            }) {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
            ).getTypedContent<ProdusentAPI.NyBeskjedResultat>("nyBeskjed")

            it("errors har forklarende feilmelding") {
                resultat should beOfType<ProdusentAPI.Error.UgyldigMerkelapp>()
                resultat as ProdusentAPI.Error.UgyldigMerkelapp
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
                                        virksomhetsnummer: "${mottaker.virksomhetsnummer}"
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
                                }
                            }) {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
            ).getTypedContent<ProdusentAPI.NyBeskjedResultat>("nyBeskjed")

            it("errors har forklarende feilmelding") {
                resultat should beOfType<ProdusentAPI.Error.UgyldigMottaker>()
                resultat as ProdusentAPI.Error.UgyldigMottaker
                resultat.feilmelding shouldContain mottaker.serviceCode
                resultat.feilmelding shouldContain mottaker.serviceEdition
            }
        }
    }
})

