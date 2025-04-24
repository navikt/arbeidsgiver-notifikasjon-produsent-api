package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientStub
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class VirksomhetsinfoServiceTest {
    private val enhetsregisteret = object : Enhetsregisteret {
        override suspend fun hentUnderenhet(orgnr: String): Enhetsregisteret.Underenhet {
            return Enhetsregisteret.Underenhet(orgnr, "ereg $orgnr")
        }
    }

    val virksomhetsinfoService = VirksomhetsinfoService(
        enhetsregisteret = enhetsregisteret,
    )

    val altinn = AltinnTilgangerClient(
        observer = virksomhetsinfoService::cachePut,
        authClient = AuthClientStub(),
        engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(//language=JSON
                    """
                    {
                      "isError": false,
                      "hierarki": [
                        {
                          "orgnr": "1",
                          "navn": "altinn 1",
                          "organisasjonsform": "AS",
                          "altinn3Tilganger": [],
                          "altinn2Tilganger": [],
                          "underenheter": [
                            {
                              "orgnr": "2",
                              "navn": "altinn 2",
                              "organisasjonsform": "BEDR",
                              "altinn3Tilganger": [],
                              "altinn2Tilganger": [],
                              "underenheter": []
                            },
                            {
                              "orgnr": "3",
                              "navn": "altinn 3",
                              "organisasjonsform": "BEDR",
                              "altinn3Tilganger": [],
                              "altinn2Tilganger": [],
                              "underenheter": []
                            }
                          ]
                        }
                      ],
                      "orgNrTilTilganger": {},
                      "tilgangTilOrgNr": {}
                    }
                """
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    )

    @Test
    fun `automatisk caching av virksomhetsnavn fra altinn-kall`() = runTest {
        // ingen cache på navnet
        assertEquals("ereg 1", virksomhetsinfoService.hentUnderenhet("1").navn)
        assertEquals("ereg 2", virksomhetsinfoService.hentUnderenhet("2").navn)


        // caching av navn forekommer
        altinn.hentTilganger("dummy token")
        assertEquals("altinn 1", virksomhetsinfoService.hentUnderenhet("1").navn)
        assertEquals("altinn 2", virksomhetsinfoService.hentUnderenhet("2").navn)
        assertEquals("altinn 3", virksomhetsinfoService.hentUnderenhet("3").navn)
        assertEquals("ereg 4", virksomhetsinfoService.hentUnderenhet("4").navn)
    }
}
