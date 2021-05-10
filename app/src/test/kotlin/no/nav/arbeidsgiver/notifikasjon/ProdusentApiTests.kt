package no.nav.arbeidsgiver.notifikasjon

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
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.createDataSource
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlin.time.toJavaDuration


@ExperimentalTime
class ProdusentApiTests : DescribeSpec({
    val altinn = object : Altinn {
        override fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<Tilgang>()
    }

    val embeddedKafka = EmbeddedKafkaTestListener()
    listener(embeddedKafka)
    val engine by ktorEngine(
        brukerGraphQL = createBrukerGraphQL(
            altinn = altinn,
            dataSourceAsync = CompletableFuture.completedFuture(runBlocking { createDataSource() }),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = createProdusentGraphQL(
            kafkaProducer = embeddedKafka.newProducer()
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
                    val consumer = embeddedKafka.newConsumer()
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
})

