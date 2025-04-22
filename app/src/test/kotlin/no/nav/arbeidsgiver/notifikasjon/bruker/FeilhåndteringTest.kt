package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeilhåndteringTest {
    @Test
    fun `Feil Altinn, DigiSyfo ok`() = ktorBrukerTestServer(
        altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
            AltinnTilganger(
                harFeil = true,
                tilganger = listOf()
            )
        },

        brukerRepository = object : BrukerRepositoryStub() {
            override suspend fun hentNotifikasjoner(
                fnr: String, altinnTilganger: AltinnTilganger
            ) = emptyList<BrukerModel.Notifikasjon>()

            override suspend fun hentSakerForNotifikasjoner(
                grupperinger: List<BrukerModel.Gruppering>
            ) = emptyMap<String, BrukerModel.SakMetadata>()
        },
    ) {
        val response = client.queryNotifikasjonerJson()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.getGraphqlErrors().isEmpty())
        assertEquals(true, response.getTypedContent<Boolean>("notifikasjoner/feilAltinn"))
        assertEquals(false, response.getTypedContent<Boolean>("notifikasjoner/feilDigiSyfo"))
    }
}


