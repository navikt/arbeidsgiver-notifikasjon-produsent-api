package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.KafkaKey
import org.apache.kafka.clients.producer.Producer
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource

class KlikkPåNotifikasjonGraphQLTest: DescribeSpec({
    val altinn: Altinn = mockk()
    val dataSource: DataSource = mockk()
    val kafkaProducer: Producer<KafkaKey, Hendelse> = mockk()

    val engine by ktorEngine(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            dataSourceAsync = CompletableFuture.completedFuture(dataSource),
            kafkaProducer = kafkaProducer
        ),
        produsentGraphQL = mockk()
    )

    describe("bruker-api: rapporterer om at notifikasjon er klikket på") {
//        beforeEach {
//            mockkObject(QueryModelRepository)
//            mockkStatic(Producer<*, *>::sendEvent)
//        }
//
//        afterEach {
//            unmockkObject(QueryModelRepository)
//            unmockkStatic()
//        }

        context("uklikket-notifikasjon eksisterer for bruker") {
            val fnr = "12345"
            val id = "4321"
            val query = """
                    mutation {
                        notifikasjonKlikketPaa(id: "$id") {
                            errors {
                                __typename
                                feilmelding
                            }
                        }
                    }
                """.trimIndent()

            val httpResponse = engine.post(
                "/api/graphql",
                host = BRUKER_HOST,
                jsonBody = GraphQLRequest(query),
                accept = "application/json",
                authorization = "Bearer $TOKENDINGS_TOKEN"
            )

            it("ingen http/graphql-feil") {
                httpResponse.status() shouldBe HttpStatusCode.OK
                httpResponse.getGraphqlErrors() should beEmpty()
            }

            val graphqlSvar = httpResponse.getTypedContent<BrukerAPI.NotifikasjonKlikketPaaResultat>("notifikasjonKlikketPaa")

            it("ingen domene-feil") {
                graphqlSvar.errors should beEmpty()
            }

//            it("backenden gjør") {
//                verify {
//                    QueryModelRepository.registereKlikkPåNotifikasjon(fnr, id)
//                }
//            }
//
//            it("backend") {
//                verify {
//                    mockedPRoducer.sendEventKlikkPåNotifikasjon(fnr, id)
//                }
//            }
        }
    }
})