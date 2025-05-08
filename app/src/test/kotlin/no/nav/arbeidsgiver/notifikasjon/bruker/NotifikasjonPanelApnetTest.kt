package no.nav.arbeidsgiver.notifikasjon.bruker

import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import kotlin.test.Test

class NotifikasjonPanelApnetTest {
    @Test
    fun `Setter notifikasjoner sist lest for bruker`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val sistLestTidspunkt = "2023-10-01T12:00:00+02:00"
        val fnr = "11223345678"

        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
        ) {
            client.brukerApi("""
                mutation {
                    notifikasjonPanelApnet(
                        tidspunkt: ${sistLestTidspunkt}
                    ) {
                        tidspunkt
                    }
                }
            """.trimIndent())
        }
    }

    fun `Oppdaterer eksisterende notifikasjoner sist lest for bruker`() =
        withTestDatabase(Bruker.databaseConfig) { database ->
            val sistLestTidspunkt = "2023-10-01T12:00:00+02:00"
            val fnr = "11223345678"
            val brukerRepository = BrukerRepositoryImpl(database)
            brukerRepository.settNotifikasjonerSistLest(OffsetDateTime.parse(sistLestTidspunkt).minusDays(4), fnr)

            ktorBrukerTestServer(
                brukerRepository = brukerRepository,
            ) {
                client.brukerApi("""
                mutation {
                    notifikasjonPanelApnet(
                        tidspunkt: ${sistLestTidspunkt}
                    ) {
                        tidspunkt
                    }
                }
            """.trimIndent())
            }
        }
}