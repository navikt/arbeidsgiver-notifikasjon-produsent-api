package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.mockserver.MockServerListener
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.arbeidsgiver.notifikasjon.bruker.VirksomhetsinfoService
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType

@Suppress("HttpUrlsUsage")
class EnhetsregisteretTests : DescribeSpec({
    listener(MockServerListener(1111))

    val host = "localhost"
    val port = 1111
    val mockServerClient = MockServerClient(host, port)
    fun mockBrregResponse(
        withContentType: HttpResponse?
    ) {
        mockServerClient.reset()
        mockServerClient.`when`(
            HttpRequest.request()
                .withMethod("GET")
                .withPath("/v1/organisasjon/$orgnr/noekkelinfo")
        ).respond(
            withContentType
        )
    }

    describe("Brreg#hentEnhet") {
        context("når enhet finnes") {
            val brreg = VirksomhetsinfoService(EnhetsregisteretImpl("http://$host:$port"))
            mockBrregResponse(
                HttpResponse.response()
                    .withBody(enhetJson, Charsets.UTF_8)
                    .withContentType(MediaType.APPLICATION_JSON)
            )
            val enhet = brreg.hentUnderenhet(orgnr)


            it("inneholder navn på enhet") {
                enhet.navn shouldBe "NAV IKT FOOBAR"
            }

            context("når det gjøres flere kall til samme enhet") {
                val enhet2 = brreg.hentUnderenhet(orgnr)

                it("enhet er samme instans") {
                    enhet2 shouldBeSameInstanceAs enhet
                }
            }
        }
        context("når enhet ikke finnes") {
            val brreg = VirksomhetsinfoService(EnhetsregisteretImpl("http://$host:$port"))
            mockBrregResponse(HttpResponse.notFoundResponse())
            val enhet = brreg.hentUnderenhet(orgnr)

            it("inneholder ikke navn på enhet") {
                enhet.navn shouldBe ""
            }
        }

        /* hvis api-et er nede, hender det de returnerer html >:( */
        context("når ereg returnerer html") {
            val brreg = VirksomhetsinfoService(EnhetsregisteretImpl("http://$host:$port"))
            mockBrregResponse(
                HttpResponse.response()
                    .withBody("<html>hello world </html>")
                    .withContentType(MediaType.TEXT_HTML))
            val enhet = brreg.hentUnderenhet(orgnr)

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
              "organisasjonsnummer": "990983666",
              "navn": {
                "navnelinje1": "NAV IKT",
                "navnelinje2": "FOOBAR",
                "navnelinje3": "",
                "navnelinje4": null,
                "bruksperiode": {
                  "fom": "2015-02-23T08:04:53.2"
                },
                "gyldighetsperiode": {
                  "fom": "2010-04-09"
                }
              },
              "enhetstype": "BEDR",
              "adresse": {
                "type": "Forretningsadresse",
                "adresselinje1": "Sannergata 2",
                "postnummer": "0557",
                "landkode": "NO",
                "kommunenummer": "0301",
                "bruksperiode": {
                  "fom": "2015-02-23T10:38:34.403"
                },
                "gyldighetsperiode": {
                  "fom": "2007-08-23"
                }
              }
            }
            """.trimIndent()
    }
}

