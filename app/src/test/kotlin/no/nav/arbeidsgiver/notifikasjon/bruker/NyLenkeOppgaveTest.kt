package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals


class NyLenkeOppgaveTest {

    @Test
    fun `Oppgave med uendret lenke`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(lenke = "https://opprettelse-lenke")

        assertEquals("https://opprettelse-lenke", brukerRepository.hentLenke())

        brukerRepository.oppgaveUtført(oppgaveOpprettet, nyLenke = null)
        assertEquals("https://opprettelse-lenke", brukerRepository.hentLenke())

        brukerRepository.oppgaveUtført(oppgaveOpprettet, nyLenke = "https://utført-lenke")
        assertEquals("https://utført-lenke", brukerRepository.hentLenke())
    }
}

private suspend fun BrukerRepository.hentLenke() =
    hentNotifikasjoner(
        fnr = "",
        altinnTilganger = AltinnTilganger(
            harFeil = false,
            tilganger = listOf(TEST_TILGANG_1)
        )
    )
        .filterIsInstance<BrukerModel.Oppgave>()
        .first()
        .lenke

