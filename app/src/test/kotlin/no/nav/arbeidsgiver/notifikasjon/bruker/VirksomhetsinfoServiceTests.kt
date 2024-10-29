package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClientStub

class VirksomhetsinfoServiceTests: DescribeSpec({
    val enhetsregisteret = object: Enhetsregisteret {
        override suspend fun hentUnderenhet(orgnr: String): Enhetsregisteret.Underenhet {
            return Enhetsregisteret.Underenhet(orgnr, "ereg $orgnr")
        }
    }

    val virksomhetsinfoService = VirksomhetsinfoService(
        enhetsregisteret = enhetsregisteret,
    )

    val altinn = AltinnTilgangerClient(
        observer = virksomhetsinfoService::cachePut,
        tokenXClient = TokenXClientStub(),
        engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(//language=JSON
                    """
                    {
                      "isError": false,
                      "hierarki": [
                        {
                          "orgNr": "1",
                          "name": "altinn 1",
                          "organizationForm": "AS",
                          "altinn3Tilganger": [],
                          "altinn2Tilganger": [],
                          "underenheter": [
                            {
                              "orgNr": "2",
                              "name": "altinn 2",
                              "organizationForm": "BEDR",
                              "altinn3Tilganger": [],
                              "altinn2Tilganger": [],
                              "underenheter": []
                            },
                            {
                              "orgNr": "3",
                              "name": "altinn 3",
                              "organizationForm": "BEDR",
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
                """),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    )

    describe("automatisk caching av virksomhetsnavn fra altinn-kall") {
        it("ingen cache på navnet") {
            virksomhetsinfoService.hentUnderenhet("1").navn shouldBe "ereg 1"
            virksomhetsinfoService.hentUnderenhet("2").navn shouldBe "ereg 2"
        }

        it("caching av navn forekommer") {
            altinn.hentTilganger("dummy token")
            virksomhetsinfoService.hentUnderenhet("1").navn shouldBe "altinn 1"
            virksomhetsinfoService.hentUnderenhet("2").navn shouldBe "altinn 2"
            virksomhetsinfoService.hentUnderenhet("3").navn shouldBe "altinn 3"
            virksomhetsinfoService.hentUnderenhet("4").navn shouldBe "ereg 4"
        }
    }
})
