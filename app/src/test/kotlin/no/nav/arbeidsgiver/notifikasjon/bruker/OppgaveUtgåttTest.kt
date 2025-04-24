package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OppgaveUtgåttTest {


    @Test
    fun `oppgave utgått`() = withTestDatabase(Bruker.databaseConfig) { database ->
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
                notifikasjonId = uuid("1"),
                opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            )
            val oppgaveUtgått = brukerRepository.oppgaveUtgått(
                oppgaveOpprettet,
                utgaattTidspunkt = OffsetDateTime.parse("2018-12-03T10:15:30+01:00"),
            )

            val oppgave = client.queryNotifikasjonerJson()
                .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

            // har tilstand utgått og utgått tidspunkt
            assertEquals(BrukerAPI.Notifikasjon.Oppgave.Tilstand.UTGAATT, oppgave.tilstand)
            assertNotNull(oppgave.utgaattTidspunkt)
            assertEquals(oppgaveUtgått.utgaattTidspunkt.toInstant(), oppgave.utgaattTidspunkt!!.toInstant())
        }
    }
}
