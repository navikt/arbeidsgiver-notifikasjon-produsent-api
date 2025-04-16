package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Kalenderavtale
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryKommendeKalenderavtalerTest {
    val now: LocalDateTime = LocalDateTime.now()

    @Test
    fun kommendeKalenderavtaler() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)

        ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
                AltinnTilganger(
                    harFeil = false,
                    tilganger = listOf(TEST_TILGANG_1, TEST_TILGANG_2)
                )
            }
        ) {
            val grupperingsid = "42"
            val merkelapp = "tag"
            val sak1 = brukerRepository.sakOpprettet(
                sakId = uuid("1"),
                mottakere = listOf(TEST_MOTTAKER_1),
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
            )
            val sak2 = brukerRepository.sakOpprettet(
                sakId = uuid("2"),
                mottakere = listOf(TEST_MOTTAKER_2),
                virksomhetsnummer = TEST_VIRKSOMHET_2,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
            )

            brukerRepository.kalenderavtaleOpprettet(
                sakId = sak1.sakId,
                virksomhetsnummer = sak1.virksomhetsnummer,
                mottakere = sak1.mottakere,
                notifikasjonId = uuid("2"),
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                startTidspunkt = now.plusDays(1),
                sluttTidspunkt = now.plusDays(1).plusHours(1),
                tekst = "2. plass"
            )
            brukerRepository.kalenderavtaleOpprettet(
                sakId = sak2.sakId,
                virksomhetsnummer = sak2.virksomhetsnummer,
                mottakere = sak2.mottakere,
                notifikasjonId = uuid("3"),
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                startTidspunkt = now.plusHours(1),
                sluttTidspunkt = now.plusHours(2),
                tekst = "1. plass"
            )
            brukerRepository.kalenderavtaleOpprettet(
                sakId = sak1.sakId,
                virksomhetsnummer = sak1.virksomhetsnummer,
                mottakere = sak1.mottakere,
                notifikasjonId = uuid("4"),
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                startTidspunkt = now.minusHours(2),
                sluttTidspunkt = now.minusHours(1),
                tekst = "DNF"
            )
            brukerRepository.kalenderavtaleOpprettet(
                sakId = sak2.sakId,
                virksomhetsnummer = sak2.virksomhetsnummer,
                mottakere = sak2.mottakere,
                notifikasjonId = uuid("5"),
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                startTidspunkt = now.minusDays(2),
                sluttTidspunkt = now.minusDays(2).plusHours(1),
                tekst = "DNF"
            )
            brukerRepository.kalenderavtaleOppdatert(
                notifikasjonId = uuid("5"),
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                startTidspunkt = now.plusDays(2),
                sluttTidspunkt = now.plusDays(2).plusHours(1),
                tekst = "fra DNF til 3. plass"
            )

            val response = client.queryKommendeKalenderavtalerJson(listOf(TEST_VIRKSOMHET_1, TEST_VIRKSOMHET_2))

            // returnerer kommende kalenderavtaler nærmest først
            val kalenderavtaler = response.getTypedContent<List<Kalenderavtale>>("kommendeKalenderavtaler/avtaler")
            assertEquals(3, kalenderavtaler.size)
            assertEquals("1. plass", kalenderavtaler[0].tekst)
            assertEquals(now.plusHours(1).atOslo().toInstant(), kalenderavtaler[0].startTidspunkt.toInstant())
            assertEquals("2. plass", kalenderavtaler[1].tekst)
            assertEquals(now.plusDays(1).atOslo().toInstant(), kalenderavtaler[1].startTidspunkt.toInstant())
            assertEquals("fra DNF til 3. plass", kalenderavtaler[2].tekst)
            assertEquals(now.plusDays(2).atOslo().toInstant(), kalenderavtaler[2].startTidspunkt.toInstant())

        }
    }
}