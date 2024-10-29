package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClientStub

class AltinnTilgangerClientTest : DescribeSpec({
    describe("AltinnTilgangerClient") {

        val client = AltinnTilgangerClient(
            tokenXClient = TokenXClientStub(),
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
                    AltinnTilgang.Altinn3("910825496", "test-fager"),
                    AltinnTilgang.Altinn2("910825496", "4936", "1"),
                )
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
          "orgNr": "810825472",
          "name": "foo",
          "altinn3Tilganger": [],
          "altinn2Tilganger": [],
          "underenheter": [
            {
              "orgNr": "910825496",
              "name": "bar",
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
