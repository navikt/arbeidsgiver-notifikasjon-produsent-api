package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OppgaveMedFristTest {
    @Test
    fun `oppgave med frist`() = withTestDatabase(Bruker.databaseConfig) { database ->
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

            // oppgave opprettet får frist
            val opprinneligFrist = LocalDate.parse("2007-07-07")
            val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(frist = opprinneligFrist)
            with(client.hentNotifikasjon()) {
                assertEquals(opprinneligFrist, frist)
                assertEquals(NY, tilstand)
            }

            // frist utsettes
            val utsattFrist = LocalDate.parse("2008-08-08")
            brukerRepository.oppgaveFristUtsatt(oppgaveOpprettet, frist = utsattFrist)
            with(client.hentNotifikasjon()) {
                assertEquals(utsattFrist, frist)
                assertEquals(NY, tilstand)
            }

            // oppgave utgår
            brukerRepository.oppgaveUtgått(
                oppgaveOpprettet,
                utgaattTidspunkt = OffsetDateTime.parse("2008-08-08T08:08:08Z")
            )
            with(client.hentNotifikasjon()) {
                assertEquals(UTGAATT, tilstand)
            }

            // frist utsettes igjen
            val utsattFrist2 = LocalDate.parse("2009-09-09")
            brukerRepository.oppgaveFristUtsatt(oppgaveOpprettet, frist = utsattFrist2)
            with(client.hentNotifikasjon()) {
                assertEquals(utsattFrist2, frist)
                assertNull(utgaattTidspunkt)
                assertEquals(NY, tilstand)
            }

            // oppgave utført forblir utført
            brukerRepository.oppgaveUtført(oppgaveOpprettet)
            brukerRepository.oppgaveFristUtsatt(oppgaveOpprettet, frist = LocalDate.parse("2010-10-10"))

            with(client.hentNotifikasjon()) {
                assertEquals(utsattFrist2, frist)
                assertNull(utgaattTidspunkt)
                assertNotNull(utfoertTidspunkt)
                assertEquals(UTFOERT, tilstand)
            }
        }
    }
}

private suspend fun HttpClient.hentNotifikasjon() =
    queryNotifikasjonerJson()
        .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")
