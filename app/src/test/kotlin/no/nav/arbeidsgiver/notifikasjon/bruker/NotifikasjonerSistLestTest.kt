package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import kotlin.test.Test

class NotifikasjonerSistLestTest {
    @Test
    fun `Setter notifikasjoner sist lest for bruker`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val tidspunkt = OffsetDateTime.parse("2023-10-01T12:00:00Z")
        val fnr = "11223345678"

        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
        ) {
            client.mutationNotifikasjonPanelÅpnet(fnr, tidspunkt)
            val sistLestTidspunkt = client.queryNotifikasjonPanelÅpnet(fnr)
            assert(sistLestTidspunkt == tidspunkt)
        }
    }

    @Test
    fun `Oppdaterer eksisterende notifikasjoner sist lest for bruker`() =
        withTestDatabase(Bruker.databaseConfig) { database ->
            val initieltTidspunkt = OffsetDateTime.parse("2023-10-01T12:00:00Z")
            val fnr = "11223345678"
            val brukerRepository = BrukerRepositoryImpl(database)
            brukerRepository.settNotifikasjonerSistLest(initieltTidspunkt, fnr)

            ktorBrukerTestServer(
                brukerRepository = brukerRepository,
            ) {
                val tidspunktFørOppdatering = client.queryNotifikasjonPanelÅpnet(fnr)
                assert(tidspunktFørOppdatering == initieltTidspunkt)

                client.mutationNotifikasjonPanelÅpnet(fnr, initieltTidspunkt.plusDays(4))
                val tidspunktEtterOppdatering = client.queryNotifikasjonPanelÅpnet(fnr)
                assert(tidspunktEtterOppdatering == initieltTidspunkt.plusDays(4))

                val rowCount = database.nonTransactionalExecuteQuery(
                    """
                    select count(*) from notifikasjoner_sist_lest                    
                """.trimIndent(), {}, {
                        getInt(1)
                    }
                ).first()
                assert(1 == rowCount)
            }
        }

    private suspend fun HttpClient.queryNotifikasjonPanelÅpnet(fnr: String): OffsetDateTime? {
        return brukerApi(
            """
                    query {
                    notifikasjonerSistLest {
                        __typename                    
                            ... on NotifikasjonerSistLest {
                                tidspunkt
                            }
                    }
                }
                """.trimIndent(), fnr = fnr
        ).getTypedContent<OffsetDateTime?>("notifikasjonerSistLest/tidspunkt")
    }

    private suspend fun HttpClient.mutationNotifikasjonPanelÅpnet(fnr: String, tidspunkt: OffsetDateTime) {
        brukerApi(
            """
                mutation {
                    notifikasjonerSistLest(
                        tidspunkt: "$tidspunkt"
                    ) {
                        __typename
                            ... on NotifikasjonerSistLest {
                                tidspunkt
                            }
                    }
                }
            """.trimIndent(), fnr = fnr
        )
    }
}