package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

fun TestApplicationEngine.produsentApi(req: GraphQLRequest): TestApplicationResponse {
    return post(
        "/api/graphql",
        host = PRODUSENT_HOST,
        jsonBody = req,
        accept = "application/json",
        authorization = "Bearer $TOKENDINGS_TOKEN"
    )
}

fun TestApplicationEngine.produsentApi(req: String): TestApplicationResponse {
    return produsentApi(GraphQLRequest(req))
}

val produsentDefinisjoner = listOf(
    Produsent(
        id = "someproducer",
        tillatteMerkelapper = listOf("tag"),
        tillatteMottakere = listOf(
            ServicecodeDefinisjon(code = "5441", version = "1"),
            NærmesteLederDefinisjon,
        )
    )
).associateBy { it.id }

val mockProdusentRegister: ProdusentRegister = mockk() {
    every {
        finn(any())
    } answers {
        produsentDefinisjoner.getOrDefault(firstArg(), Produsent(firstArg()))
    }
}

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class ProdusentApiTests : DescribeSpec({
    val altinn = object : Altinn {
        override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<QueryModel.Tilgang>()
    }
    val brreg: Brreg = mockk()

    val embeddedKafka = EmbeddedKafkaTestListener()
    listener(embeddedKafka)
    val engine = ktorTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            brreg = brreg,
            queryModelFuture = mockk(),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = embeddedKafka.newProducer(),
            produsentRegister = mockProdusentRegister
        )
    )

    describe("POST produsent-api /api/graphql") {
        context("Mutation.nyBeskjed") {
            val response = engine.produsentApi(
                """
                    mutation {
                        nyBeskjed(nyBeskjed: {
                            lenke: "https://foo.bar",
                            tekst: "hello world",
                            merkelapp: "tag",
                            eksternId: "heu",
                            mottaker: {
                                naermesteLeder: {
                                    naermesteLederFnr: "12345678910",
                                    ansattFnr: "321"
                                    virksomhetsnummer: "42"
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

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }

            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            it("respons inneholder forventet data") {
                val nyBeskjed = response.getTypedContent<ProdusentAPI.NyBeskjedResultat>("nyBeskjed")
                nyBeskjed should beOfType<ProdusentAPI.NyBeskjedVellykket>()
            }

            it("sends message to kafka") {
                val consumer = embeddedKafka.newConsumer()
                val poll = consumer.poll(seconds(5).toJavaDuration())
                val value = poll.last().value()
                value should beOfType<Hendelse.BeskjedOpprettet>()
                val event = value as Hendelse.BeskjedOpprettet
                val nyBeskjed = response.getTypedContent<ProdusentAPI.NyBeskjedVellykket>("nyBeskjed")
                event.id shouldBe nyBeskjed.id
                event.lenke shouldBe "https://foo.bar"
                event.tekst shouldBe "hello world"
                event.merkelapp shouldBe "tag"
                event.mottaker shouldBe NærmesteLederMottaker(
                    naermesteLederFnr = "12345678910",
                    ansattFnr = "321",
                    virksomhetsnummer = "42"
                )
                event.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
            }

            context("når produsent mangler tilgang til merkelapp") {
                val merkelapp = "foo-bar"
                val response = engine.produsentApi(
                    """
                        mutation {
                            nyBeskjed(nyBeskjed: {
                                lenke: "https://foo.bar",
                                tekst: "hello world",
                                merkelapp: "$merkelapp",
                                eksternId: "heu",
                                mottaker: {
                                    naermesteLeder: {
                                        naermesteLederFnr: "12345678910",
                                        ansattFnr: "321234"
                                        virksomhetsnummer: "42"
                                    } 
                                }
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }) {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
                )

                context("response inneholder forventet data") {
                    val resultat = response.getTypedContent<ProdusentAPI.NyBeskjedResultat>("nyBeskjed")
                    it("errors har forklarende feilmelding") {
                        resultat should beOfType<ProdusentAPI.Error.UgyldigMerkelapp>()
                        resultat as ProdusentAPI.Error.UgyldigMerkelapp
                        resultat.feilmelding shouldContain merkelapp
                    }
                }
            }

            context("når produsent mangler tilgang til mottaker") {
                val mottaker = AltinnMottaker(
                    serviceCode = "1337",
                    serviceEdition = "3",
                    virksomhetsnummer = "42"
                )
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
                                        serviceCode: "${mottaker.serviceCode}",
                                        serviceEdition: "${mottaker.serviceEdition}"
                                        virksomhetsnummer: "${mottaker.virksomhetsnummer}"
                                    } 
                                }
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }) {
                                __typename
                                ... on Error {
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
                )

                context("response inneholder forventet data") {
                    val resultat = response.getTypedContent<ProdusentAPI.NyBeskjedResultat>("nyBeskjed")
                    it("errors har forklarende feilmelding") {
                        resultat should beOfType<ProdusentAPI.Error.UgyldigMottaker>()
                        resultat as ProdusentAPI.Error.UgyldigMottaker
                        resultat.feilmelding shouldContain mottaker.serviceCode
                        resultat.feilmelding shouldContain mottaker.serviceEdition
                    }
                }
            }

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

        it("status is 200 OK") {
            response.status() shouldBe HttpStatusCode.OK
        }

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

        it("status is 200 OK") {
            response.status() shouldBe HttpStatusCode.OK
        }

        it("response inneholder ikke feil") {
            response.getGraphqlErrors()[0].message shouldContainIgnoringCase "nøyaktig ett felt"
        }
    }
})

