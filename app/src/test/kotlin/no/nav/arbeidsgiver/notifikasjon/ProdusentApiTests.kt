package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
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

@Suppress("NAME_SHADOWING")
@ExperimentalTime
class ProdusentApiTests : DescribeSpec({
    val altinn = object : Altinn {
        override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<QueryModel.Tilgang>()
    }

    val embeddedKafka = EmbeddedKafkaTestListener()
    listener(embeddedKafka)
    val engine = ktorTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            queryModelFuture = mockk(),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = embeddedKafka.newProducer()
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
                                fnr: {
                                    fodselsnummer: "12345678910",
                                    virksomhetsnummer: "42"
                                } 
                            }
                            opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                        }) {
                            id
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
                val nyBeskjed = response.getTypedContent<ProdusentAPI.BeskjedResultat>("nyBeskjed")
                nyBeskjed.id shouldNot beNull()
            }

            it("sends message to kafka") {
                val consumer = embeddedKafka.newConsumer()
                val poll = consumer.poll(seconds(5).toJavaDuration())
                val value = poll.last().value()
                value should beOfType<Hendelse.BeskjedOpprettet>()
                val event = value as Hendelse.BeskjedOpprettet
                val nyBeskjed = response.getTypedContent<ProdusentAPI.BeskjedResultat>("nyBeskjed")
                event.id shouldBe nyBeskjed.id
                event.lenke shouldBe "https://foo.bar"
                event.tekst shouldBe "hello world"
                event.merkelapp shouldBe "tag"
                event.mottaker shouldBe FodselsnummerMottaker(
                    fodselsnummer = "12345678910",
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
                                    fnr: {
                                        fodselsnummer: "12345678910",
                                        virksomhetsnummer: "42"
                                    } 
                                }
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }) {
                                id
                                errors {
                                    __typename
                                    feilmelding
                                }
                            }
                        }
                    """.trimIndent()
                )

                context("response inneholder forventet data") {
                    val resultat = response.getTypedContent<ProdusentAPI.BeskjedResultat>("nyBeskjed")
                    it("id er null") {
                        resultat.id shouldBe null
                    }
                    it("errors har forklarende feilmelding") {
                        resultat.errors shouldHaveSize 1
                        resultat.errors.first() should beOfType<ProdusentAPI.MutationError.UgyldigMerkelapp>()
                        resultat.errors.first().feilmelding shouldContain merkelapp
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
                                    fnr: {
                                        fodselsnummer: "12345678910",
                                        virksomhetsnummer: "42"
                                    } 
                                }
                                opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                            }) {
                                id
                                errors {
                                    __typename
                                    feilmelding
                                }
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
})

