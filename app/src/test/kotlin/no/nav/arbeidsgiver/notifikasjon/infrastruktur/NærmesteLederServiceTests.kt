package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.mockserver.MockServerListener
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.MediaType


@Suppress("HttpUrlsUsage")
class NærmesteLederServiceTests : DescribeSpec({
    listener(MockServerListener(1111))

    val host = "localhost"
    val port = 1111
    val url = "/api"
    val token = "j.r.r.token"
    val service = NærmesteLederServiceImpl("http://$host:$port/api")
    val mockServerClient = MockServerClient(host, port)

    describe("NærmesteLederService#hentAnsatte") {
        fun mockNærmestelederRespons(responseBody: String) {
            mockServerClient.reset()
            mockServerClient.`when`(
                HttpRequest.request()
                    .withMethod("GET")
                    .withPath(url)
            ).respond(
                HttpResponse.response()
                    .withBody(responseBody, Charsets.UTF_8)
                    .withContentType(MediaType.APPLICATION_JSON)
            )
        }

        context("når tjeneste returnerer ingen ansatte") {
            mockNærmestelederRespons("""{ "ansatte": [] }""")
            val ansatte = service.hentAnsatte(token)

            it("er tom liste") {
                ansatte shouldBe emptyList()
            }
        }

        context("når tjeneste returnerer ansatte") {
            mockNærmestelederRespons("""{
              "ansatte": [
                {
                  "fnr": "03018722843",
                  "navn": "Molefonken Bamse",
                  "orgnummer": "972674818",
                  "narmestelederId": "e7e50310-7a70-4110-9ffd-9f2eee203f44"
                },
                {
                  "fnr": "03018722843",
                  "navn": "Molefonken Bamse",
                  "orgnummer": "972674818",
                  "narmestelederId": "b5b3795f-17f5-455e-92ed-85533781f028"
                }
              ]
            }""")
            val ansatte = service.hentAnsatte(token)

            it("inneholder ansatte") {
                ansatte shouldNotBe emptyList<NærmesteLederService.NærmesteLederFor>()
            }
        }
    }
})
