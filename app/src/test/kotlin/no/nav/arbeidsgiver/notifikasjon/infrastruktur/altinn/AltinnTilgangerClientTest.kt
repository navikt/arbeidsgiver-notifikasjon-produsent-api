package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientStub
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenResponse

class AltinnTilgangerClientTest : DescribeSpec({
    describe("AltinnTilgangerClient") {
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

        it("returns all tilganger") {
            client.hentTilganger("fake tolkien").also {
                it.harFeil shouldBe true
                it.tilganger shouldContainExactlyInAnyOrder listOf(
                    AltinnTilgang("910825496", "test-fager"),
                    AltinnTilgang("910825496", "4936:1"),
                )
                authClientCapture[":fager:arbeidsgiver-altinn-tilganger"] shouldBe "fake tolkien"
            }
        }
    }

})

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
