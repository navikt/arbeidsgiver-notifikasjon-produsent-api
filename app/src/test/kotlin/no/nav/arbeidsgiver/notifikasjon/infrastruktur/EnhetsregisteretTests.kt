package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.mockserver.MockServerListener
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType

@Suppress("HttpUrlsUsage")
class EnhetsregisteretTests : DescribeSpec({
    listener(MockServerListener(1111))

    val host = "localhost"
    val port = 1111
    val brreg = EnhetsregisteretImpl("http://$host:$port")
    val mockServerClient = MockServerClient(host, port)
    fun mockBrregResponse(
        withContentType: HttpResponse?
    ) {
        mockServerClient.reset()
        mockServerClient.`when`(
            HttpRequest.request()
                .withMethod("GET")
                .withPath("/enhetsregisteret/api/enheter/$orgnr")
        ).respond(
            withContentType
        )
    }

    describe("Brreg#hentEnhet") {
        context("når enhet finnes") {
            mockBrregResponse(
                HttpResponse.response()
                    .withBody(enhetJson, Charsets.UTF_8)
                    .withContentType(MediaType.APPLICATION_JSON)
            )
            val enhet = brreg.hentEnhet(orgnr)


            it("inneholder navn på enhet") {
                enhet.navn shouldBe "ARBEIDS- OG VELFERDSETATEN"
            }

            context("når det gjøres flere kall til samme enhet") {
                val enhet2 = brreg.hentEnhet(orgnr)

                it("enhet er samme instans") {
                    enhet2 shouldBeSameInstanceAs enhet
                }
            }
        }
        context("når enhet ikke finnes") {
            mockBrregResponse(HttpResponse.notFoundResponse())
            brreg.cache.clear()
            val enhet = brreg.hentEnhet(orgnr)

            it("inneholder ikke navn på enhet") {
                enhet.navn shouldBe ""
            }
        }
    }
}) {
    companion object {
        private const val orgnr = "889640782"
        private val enhetJson = """
            {
              "organisasjonsnummer": "889640782",
              "navn": "ARBEIDS- OG VELFERDSETATEN",
              "organisasjonsform": {
                "kode": "ORGL",
                "beskrivelse": "Organisasjonsledd",
                "_links": {
                  "self": {
                    "href": "https://data.brreg.no/enhetsregisteret/api/organisasjonsformer/ORGL"
                  }
                }
              },
              "hjemmeside": "www.nav.no",
              "postadresse": {
                "land": "Norge",
                "landkode": "NO",
                "postnummer": "8601",
                "poststed": "MO I RANA",
                "adresse": [
                  "Postboks 354"
                ],
                "kommune": "RANA",
                "kommunenummer": "1833"
              },
              "registreringsdatoEnhetsregisteret": "2006-03-23",
              "registrertIMvaregisteret": true,
              "naeringskode1": {
                "beskrivelse": "Offentlig administrasjon tilknyttet helsestell, sosial virksomhet, undervisning, kirke, kultur og miljøvern",
                "kode": "84.120"
              },
              "antallAnsatte": 1608,
              "overordnetEnhet": "983887457",
              "forretningsadresse": {
                "land": "Norge",
                "landkode": "NO",
                "postnummer": "0661",
                "poststed": "OSLO",
                "adresse": [
                  "Fyrstikkalléen 1"
                ],
                "kommune": "OSLO",
                "kommunenummer": "0301"
              },
              "institusjonellSektorkode": {
                "kode": "6100",
                "beskrivelse": "Statsforvaltningen"
              },
              "registrertIForetaksregisteret": false,
              "registrertIStiftelsesregisteret": false,
              "registrertIFrivillighetsregisteret": false,
              "konkurs": false,
              "underAvvikling": false,
              "underTvangsavviklingEllerTvangsopplosning": false,
              "maalform": "Bokmål",
              "_links": {
                "self": {
                  "href": "https://data.brreg.no/enhetsregisteret/api/enheter/889640782"
                },
                "overordnetEnhet": {
                  "href": "https://data.brreg.no/enhetsregisteret/api/enheter/983887457"
                }
              }
            }
            """.trimIndent()
    }
}

