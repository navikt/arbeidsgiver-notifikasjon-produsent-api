package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import io.mockk.every
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.produsent.api.Error
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationNyBeskjed
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import kotlin.time.ExperimentalTime

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class TilgangsstyringTests : DescribeSpec({
    val engine = ktorProdusentTestServer(
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk(),
            produsentRepository = mockk {
                every { altinnRolle } returns mockk()
            }
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

