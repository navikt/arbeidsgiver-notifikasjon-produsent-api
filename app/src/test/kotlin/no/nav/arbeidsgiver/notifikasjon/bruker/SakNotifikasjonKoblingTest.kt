package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SakNotifikasjonKoblingTest {
    @Test
    fun `sak og oppgave med samme grupperingsid, men forskjellig merkelapp `() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(TEST_TILGANG_1)
                )
            }
        ) {
            brukerRepository.oppgaveOpprettet(
                grupperingsid = "gruppe1",
                merkelapp = "X",

                )
            brukerRepository.sakOpprettet(
                grupperingsid = "gruppe1",
                merkelapp = "Z",
            ).also {
                brukerRepository.nyStatusSak(sak = it, idempotensKey = "")
            }

            // sak og oppgave eksisterer hver for seg
            with(
                client.querySakerJson()
                    .getTypedContent<List<Any>>("$.saker.saker")
            ) {
                assertEquals(1, size)
            }

            with(
                client.queryNotifikasjonerJson()
                    .getTypedContent<List<Any>>("$.notifikasjoner.notifikasjoner")
            ) {
                assertEquals(1, size)
            }

            // oppgave dukker ikke opp i tidslinje
            with(
                client.querySakerJson()
                    .getTypedContent<List<Any>>("$.saker.saker[0].tidslinje")
            ) {
                assertTrue(isEmpty())
            }

            // sak dukker ikke opp på oppgave
            with(
                client.queryNotifikasjonerJson()
                    .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("$.notifikasjoner.notifikasjoner[0]")
            ) {

                assertNull(sak)
            }

        }
    }
}