package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.convertValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.createDataSource
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlin.time.toJavaDuration

data class GraphQLError(
    val message: String,
    val locations: Any?,
    val extensions: Map<String, Any>?
)

inline fun <reified T> TestApplicationResponse.getTypedContent(name: String): T {
    val errors = getGraphqlErrors()
    if (errors.isEmpty()) {
        val tree = objectMapper.readTree(this.content!!)
        val node = tree.get("data").get(name)
        return objectMapper.convertValue(node)
    } else {
        throw Exception("Got errors $errors")
    }
}

fun TestApplicationResponse.getGraphqlErrors(): List<GraphQLError> {
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val errors = tree.get("errors")
    return if (errors == null) emptyList() else objectMapper.convertValue(errors)
}

@ExperimentalTime
class GraphQLTests : DescribeSpec({
    val altinn = object : Altinn {
        override fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<Tilgang>()
    }

    listener(EmbeddedKafkaTestListener())
    val engine by ktorEngine(
        brukerGraphQL = createBrukerGraphQL(
            altinn = altinn,
            dataSourceAsync = CompletableFuture.completedFuture(runBlocking { createDataSource() })
        ),
        produsentGraphQL = createProdusentGraphQL(
            kafkaProducer = EmbeddedKafka.producer
        )
    )

    describe("POST produsent-api /api/graphql") {
        lateinit var response: TestApplicationResponse
        lateinit var query: String

        beforeEach {
            response = engine.post(
                "/api/graphql",
                host = PRODUSENT_HOST,
                jsonBody = GraphQLRequest(query),
                accept = "application/json",
                authorization = "Bearer $TOKENDINGS_TOKEN"
            )
        }
        context("Mutation.nyBeskjed") {
            query = """
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

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            context("respons er parsed som BeskjedResultat") {
                lateinit var resultat: BeskjedResultat

                beforeEach {
                    resultat = response.getTypedContent("nyBeskjed")
                }

                it("id er gitt") {
                    resultat.id shouldNot beBlank()
                }

                it("sends message to kafka") {
                    val consumer = EmbeddedKafka.consumer
                    val poll = consumer.poll(5.seconds.toJavaDuration())
                    val value = poll.last().value()
                    value should beOfType<BeskjedOpprettet>()
                    val event = value as BeskjedOpprettet
                    event.guid.toString() shouldBe resultat.id
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
                    query = """
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

                    it("id er null") {
                        resultat.id shouldBe null
                    }

                    it("errors har forklarende feilmelding") {
                        resultat.errors shouldHaveSize 1
                        resultat.errors.first() should beOfType<UgyldigMerkelapp>()
                        resultat.errors.first().feilmelding shouldContain merkelapp
                    }
                }
            }
        }
    }

    describe("POST bruker-api /api/graphql") {
        lateinit var response: TestApplicationResponse
        lateinit var query: String
        val beskjed = QueryBeskjed(
            merkelapp = "foo",
            tekst = "",
            grupperingsid = "",
            lenke = "",
            eksternId = "",
            mottaker = FodselsnummerMottaker("00000000000", "43"),
            opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00")
        )

        beforeEach {
            mockkObject(QueryModelRepository)
            coEvery {
                QueryModelRepository.hentNotifikasjoner(any(), any(), any())
            } returns listOf(beskjed)

            response = engine.post("/api/graphql",
                host = BRUKER_HOST,
                jsonBody = GraphQLRequest(query),
                accept = "application/json",
                authorization = "Bearer $SELVBETJENING_TOKEN"
            )
        }
        afterEach {
            unmockkObject(QueryModelRepository)
        }
        context("Query.notifikasjoner") {
            query = """
                {
                    notifikasjoner {
                        ...on Beskjed {
                            lenke
                            tekst
                            merkelapp
                            opprettetTidspunkt
                        }
                    }
                }
            """.trimIndent()

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            context("respons er parsed som liste av Beskjed") {
                lateinit var resultat: List<Beskjed>

                beforeEach {
                    resultat = response.getTypedContent("notifikasjoner")
                }

                it("returnerer beskjeden fra repo") {
                    resultat shouldNot beEmpty()
                    resultat[0].merkelapp shouldBe beskjed.merkelapp
                }
            }
        }
    }
})

