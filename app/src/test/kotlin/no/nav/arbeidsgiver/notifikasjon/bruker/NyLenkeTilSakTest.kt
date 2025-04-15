package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class NyLenkeTilSakTest {
    private suspend fun HttpClient.hentLenke() = querySakerJson(
        virksomhetsnummer = TEST_VIRKSOMHET_1,
        offset = 0,
        limit = 1,
    ).getTypedContent<String>("saker/saker/0/lenke")

    private suspend fun HttpClient.hentSisteStatus() = querySakerJson(
        virksomhetsnummer = TEST_VIRKSOMHET_1,
        offset = 0,
        limit = 1,
    ).getTypedContent<BrukerAPI.SakStatus>("saker/saker/0/sisteStatus")


    @Test
    fun `endring av lenke i sak`() = withTestDatabase(Bruker.databaseConfig) { database ->
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
            val sakOpprettet = brukerRepository.sakOpprettet(lenke = "#foo")
            brukerRepository.nyStatusSak(sak = sakOpprettet, idempotensKey = IdempotenceKey.initial())

            assertEquals("#foo", client.hentLenke())

            brukerRepository.nyStatusSak(
                sak = sakOpprettet,
                idempotensKey = IdempotenceKey.userSupplied("20202021"),
                nyLenkeTilSak = "#bar",
                oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T12:00:00Z"),
            )

            assertEquals("#bar", client.hentLenke())
            assertEquals(OffsetDateTime.parse("2020-01-01T12:00:00Z"), client.hentSisteStatus().tidspunkt)

            brukerRepository.nyStatusSak(
                sak = sakOpprettet,
                idempotensKey = IdempotenceKey.userSupplied("123"),
                nyLenkeTilSak = null,
            )

            assertEquals("#bar", client.hentLenke())
        }
    }
}