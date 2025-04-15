package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientStub
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class AltinnTilgangerClientTest {
    @Test
    fun `returns all tilganger`() = runTest {
        val authClientCapture = mutableMapOf<String, String>()
        val client = AltinnTilgangerClient(
            authClient = object : AuthClientStub() {
                override suspend fun exchange(target: String, userToken: String): TokenResponse {
                    authClientCapture[target] = userToken
                    return TokenResponse.Success("foo", 42)
                }
            },
            observer = { _, _ -> },
            engine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel(altinnTilgangerResponse),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )

        client.hentTilganger("fake tolkien").also {
            assertEquals(true, it.harFeil)
            assertEquals(
                listOf(AltinnTilgang("910825496", "test-fager"), AltinnTilgang("910825496", "4936:1")).sortedBy { t -> t.tilgang },
                it.tilganger.sortedBy { t -> t.tilgang }
            )
            assertEquals("fake tolkien", authClientCapture[":fager:arbeidsgiver-altinn-tilganger"])
        }
    }

}

//language=JSON
private val altinnTilgangerResponse = """
    {
      "isError": true,
      "hierarki": [
        {
          "orgnr": "810825472",
          "navn": "foo",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [],
          "underenheter": [
            {
              "orgnr": "910825496",
              "navn": "bar",
              "altinn3Tilganger": [
                "test-fager"
              ],
              "altinn2Tilganger": [
                "4936:1"
              ],
              "underenheter": []
            }
          ]
        }
      ],
      "orgNrTilTilganger": {
        "910825496": [
          "test-fager",
          "4936:1"
        ]
      },
      "tilgangTilOrgNr": {
        "test-fager": [
          "910825496"
        ],
        "4936:1": [
          "910825496"
        ]
      }
    }
""".trimIndent()
