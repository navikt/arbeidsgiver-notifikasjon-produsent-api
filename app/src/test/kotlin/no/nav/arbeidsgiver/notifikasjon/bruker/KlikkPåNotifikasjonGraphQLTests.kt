package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.types.beOfType
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*

class KlikkPåNotifikasjonGraphQLTests : DescribeSpec({
    val brukerRepository = object : BrukerRepositoryStub() {
        val hendelser = mutableListOf<HendelseModel.Hendelse>()
        override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String {
            return "1234"
        }

        override suspend fun oppdaterModellEtterHendelse(
            hendelse: HendelseModel.Hendelse,
            metadata: HendelseModel.HendelseMetadata
        ) {
          hendelser.add(hendelse)
        }
    }
    val kafkaProducer = FakeHendelseProdusent()

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        kafkaProducer = kafkaProducer,
    )

    describe("bruker-api: rapporterer om at notifikasjon er klikket på") {
        context("uklikket-notifikasjon eksisterer for bruker") {
            val id = UUID.fromString("09d5a598-b31a-11eb-8529-0242ac130003")
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

            val graphqlSvar =
                httpResponse.getTypedContent<BrukerAPI.NotifikasjonKlikketPaaResultat>("notifikasjonKlikketPaa")

            it("ingen domene-feil") {
                graphqlSvar should beOfType<BrukerAPI.BrukerKlikk>()
            }

            it("response inneholder klikketPaa og id") {
                val brukerKlikk = graphqlSvar as BrukerAPI.BrukerKlikk
                brukerKlikk.klikketPaa shouldBe true
                brukerKlikk.id shouldNot beBlank()
            }

            it("Event produseres på kafka") {
                kafkaProducer.hendelser.first().let {
                    it should beOfType<BrukerKlikket>()
                    it as BrukerKlikket
                    it.fnr shouldNot beBlank()
                    it.notifikasjonId shouldBe id
                    it.virksomhetsnummer shouldNot beBlank()
                }
            }

            it("Database oppdaters") {
                brukerRepository.hendelser.first().let {
                    it should beOfType<BrukerKlikket>()
                    it as BrukerKlikket
                    it.fnr shouldNot beBlank()
                    it.notifikasjonId shouldBe id
                    it.virksomhetsnummer shouldNot beBlank()
                }
            }
        }
    }
})