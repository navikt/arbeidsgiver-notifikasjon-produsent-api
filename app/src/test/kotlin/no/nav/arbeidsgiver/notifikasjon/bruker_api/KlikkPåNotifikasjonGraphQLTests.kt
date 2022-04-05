package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import io.mockk.*
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*

class KlikkPåNotifikasjonGraphQLTests: DescribeSpec({
    val queryModel: BrukerRepositoryImpl = mockk(relaxed = true)
    val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse> = mockk()

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
        kafkaProducer = kafkaProducer,
    )

    mockkStatic(CoroutineKafkaProducer<KafkaKey, Hendelse>::sendHendelse)
    coEvery { any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(any<BrukerKlikket>()) } returns Unit

    afterSpec {
        unmockkAll()
    }

    describe("bruker-api: rapporterer om at notifikasjon er klikket på") {
        context("uklikket-notifikasjon eksisterer for bruker") {
            val id = UUID.fromString("09d5a598-b31a-11eb-8529-0242ac130003")
            coEvery { queryModel.virksomhetsnummerForNotifikasjon(id) } returns "1234"

            val httpResponse = engine.post(
                "/api/graphql",
                host = BRUKER_HOST,
                jsonBody = GraphQLRequest(
                    """
                        mutation {
                            notifikasjonKlikketPaa(id: "$id") {
                                __typename
                                ... on BrukerKlikk {
                                    id
                                    klikketPaa
                                }
                            }
                        }
                    """.trimIndent()
                ),
                accept = "application/json",
                authorization = "Bearer $SELVBETJENING_TOKEN"
            )

            it("ingen http/graphql-feil") {
                httpResponse.status() shouldBe HttpStatusCode.OK
                httpResponse.getGraphqlErrors() should beEmpty()
            }

            val graphqlSvar = httpResponse.getTypedContent<BrukerAPI.NotifikasjonKlikketPaaResultat>("notifikasjonKlikketPaa")

            it("ingen domene-feil") {
                graphqlSvar should beOfType<BrukerAPI.BrukerKlikk>()
            }

            it("response inneholder klikketPaa og id") {
                val brukerKlikk = graphqlSvar as BrukerAPI.BrukerKlikk
                brukerKlikk.klikketPaa shouldBe true
                brukerKlikk.id shouldNot beBlank()
            }

            val brukerKlikketMatcher: MockKAssertScope.(BrukerKlikket) -> Unit = { brukerKlikket ->
                brukerKlikket.fnr shouldNot beBlank()
                brukerKlikket.notifikasjonId shouldBe id

                /* For øyeblikket feiler denne, siden virksomhetsnummer ikke hentes ut. */
                brukerKlikket.virksomhetsnummer shouldNot beBlank()
            }

            it("Event produseres på kafka") {
                coVerify {
                    any<CoroutineKafkaProducer<KafkaKey, Hendelse>>().sendHendelse(
                        withArg(brukerKlikketMatcher)
                    )
                }
            }

            it("Database oppdaters") {
                coVerify {
                    queryModel.oppdaterModellEtterHendelse(
                        withArg(brukerKlikketMatcher)
                    )
                }
            }
        }
    }
})