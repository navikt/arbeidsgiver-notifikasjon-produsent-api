package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.bruker.VirksomhetsinfoService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EnhetsregisteretTest {

    private fun virksomhetsinfoService(mock: MockRequestHandleScope.() -> HttpResponseData) =
        VirksomhetsinfoService(
            EnhetsregisteretImpl(
                HttpClient(MockEngine {
                    mock()
                }) {
                    expectSuccess = true
                    install(ContentNegotiation) {
                        jackson()
                    }
                }
            )
        )

    @Test
    fun `hentEnhet når enhet finnes`() = runTest {
        val service = virksomhetsinfoService {
            respond(
                content = enhetJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val enhet = service.hentUnderenhet(orgnr)
        // inneholder navn på enhet
        assertEquals("NAV IKT FOOBAR", enhet.navn)

        val enhet2 = service.hentUnderenhet(orgnr)
        // enhet er samme instans
        assertSame(enhet, enhet2)
    }


    @Test
    fun `når enhet ikke finnes`() = runTest {
        val brreg = virksomhetsinfoService {
            respondError(HttpStatusCode.NotFound)
        }

        val enhet = brreg.hentUnderenhet(orgnr)

        // inneholder ikke navn på enhet
        assertEquals("", enhet.navn)
    }

    /* hvis api-et er nede, hender det de returnerer html >:( */
    @Test
    fun `når ereg returnerer html`() = runTest {
        val brreg = virksomhetsinfoService {
            respond("<html>hello world </html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        val enhet = brreg.hentUnderenhet(orgnr)

        // inneholder ikke navn på enhet
        assertEquals("", enhet.navn)
    }
}

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


