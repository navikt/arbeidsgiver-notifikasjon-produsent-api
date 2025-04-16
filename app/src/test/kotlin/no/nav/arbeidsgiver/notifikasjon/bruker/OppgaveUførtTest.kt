package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OppgaveUførtTest {


    @Test
    fun `oppgave utført`() = withTestDatabase(Bruker.databaseConfig) { database ->
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
            val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(
                notifikasjonId = uuid("0"),
                opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            )
            val oppgaveUtført = brukerRepository.oppgaveUtført(
                oppgaveOpprettet,
                utfoertTidspunkt = OffsetDateTime.parse("2018-12-03T10:15:30+01:00"),
            )

            val oppgave = client.queryNotifikasjonerJson()
                .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

            // har tilstand utført og utført tidspunkt
            assertEquals(BrukerAPI.Notifikasjon.Oppgave.Tilstand.UTFOERT, oppgave.tilstand)
            assertNotNull(oppgave.utfoertTidspunkt)
            assertEquals(oppgaveUtført.utfoertTidspunkt?.toInstant(), oppgave.utfoertTidspunkt!!.toInstant())
        }
    }
}
