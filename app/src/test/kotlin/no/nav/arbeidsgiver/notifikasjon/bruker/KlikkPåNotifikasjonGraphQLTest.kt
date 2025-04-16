package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KlikkPåNotifikasjonGraphQLTest {
    val brukerRepositoryHendelser = mutableListOf<HendelseModel.Hendelse>()
    val brukerRepository = object : BrukerRepositoryStub() {

        override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String {
            return "1234"
        }

        override suspend fun oppdaterModellEtterHendelse(
            hendelse: HendelseModel.Hendelse,
            metadata: HendelseModel.HendelseMetadata
        ) {
            brukerRepositoryHendelser.add(hendelse)
        }
    }
    val kafkaProducer = FakeHendelseProdusent()


    @Test
    fun `uklikket-notifikasjon eksisterer for bruker`() = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        kafkaProducer = kafkaProducer,
    ) {
        val id = UUID.fromString("09d5a598-b31a-11eb-8529-0242ac130003")
        val httpResponse = client.post(
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
            authorization = "Bearer $BRUKERAPI_OBOTOKEN"
        )

        assertEquals(HttpStatusCode.OK, httpResponse.status)
        assertTrue(httpResponse.getGraphqlErrors().isEmpty())

        with(httpResponse.getTypedContent<BrukerAPI.NotifikasjonKlikketPaaResultat>("notifikasjonKlikketPaa")) {
            this as BrukerAPI.BrukerKlikk
            assertTrue(klikketPaa)
            assertFalse(this.id.isBlank())
        }

        with(kafkaProducer.hendelser.first()) {
            this as BrukerKlikket
            assertFalse(fnr.isBlank())
            assertEquals(id, notifikasjonId)
            assertFalse(virksomhetsnummer.isBlank())
        }

        with(brukerRepositoryHendelser.first()) {
            this as BrukerKlikket
            assertFalse(fnr.isBlank())
            assertEquals(id, notifikasjonId)
            assertFalse(virksomhetsnummer.isBlank())
        }
    }
}