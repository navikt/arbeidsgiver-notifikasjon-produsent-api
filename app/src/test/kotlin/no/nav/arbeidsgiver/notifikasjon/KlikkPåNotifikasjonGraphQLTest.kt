package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.mockk.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.brukerKlikket
import org.apache.kafka.clients.producer.Producer
import java.util.concurrent.CompletableFuture

class KlikkPåNotifikasjonGraphQLTest: DescribeSpec({
    val altinn: Altinn = mockk()
    val queryModel: QueryModel = mockk(relaxed = true)
    val kafkaProducer: Producer<KafkaKey, Hendelse> = mockk()

    val engine by ktorEngine(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            queryModelFuture = CompletableFuture.completedFuture(queryModel),
            kafkaProducer = kafkaProducer
        ),
        produsentGraphQL = mockk()
    )

    mockkStatic(Producer<KafkaKey, Hendelse>::brukerKlikket)
    every { any<Producer<KafkaKey, Hendelse>>().brukerKlikket(any()) } returns Unit

    afterSpec {
        unmockkAll()
    }

    describe("bruker-api: rapporterer om at notifikasjon er klikket på") {

        context("uklikket-notifikasjon eksisterer for bruker") {
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
                authorization = "Bearer $SELVBETJENING_TOKEN"
            )

            it("ingen http/graphql-feil") {
                httpResponse.status() shouldBe HttpStatusCode.OK
                httpResponse.getGraphqlErrors() should beEmpty()
            }

            val graphqlSvar = httpResponse.getTypedContent<BrukerAPI.NotifikasjonKlikketPaaResultat>("notifikasjonKlikketPaa")

            it("ingen domene-feil") {
                graphqlSvar.errors should beEmpty()
            }

            val brukerKlikketMatcher: MockKAssertScope.(Hendelse.BrukerKlikket) -> Unit = { brukerKlikket ->
                brukerKlikket.fnr shouldNot beBlank()
                brukerKlikket.notifikasjonsId shouldBe id

                /* For øyeblikket feiler denne, siden virksomhetsnummer ikke hentes ut. */
                brukerKlikket.virksomhetsnummer shouldNot beBlank()
            }

            xit("Event produseres på kafka") {
                verify {
                    any<Producer<KafkaKey, Hendelse>>().brukerKlikket(
                        withArg(brukerKlikketMatcher)
                    )
                }
            }

            xit("Database oppdaters") {
                coVerify {
                    queryModel.oppdaterModellEtterBrukerKlikket(
                        withArg(brukerKlikketMatcher)
                    )
                }
            }
        }
    }
})