package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.call.body
import io.ktor.client.call.body
import io.ktor.client.call.body
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import org.slf4j.MDC


class PropagateFromMDCPluginTests : DescribeSpec({
    describe("PropagateFromMDCPlugin") {
        val mdcKey = "foo"
        val mdcValue = "42"
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
                headers = request.headers
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(PropagateFromMDCPlugin) {
                propagate(mdcKey)
            }
        }

        afterEach {
            MDC.clear()
        }

        context("MDC inneholder nøkkel som skal propageres") {
            MDC.put(mdcKey, mdcValue)
            it("verdi fra MDC key blir propagert") {
                val response: HttpResponse = httpClient.get("")
                response.headers[mdcKey] shouldBe mdcValue
            }
        }

        context("MDC inneholder ikke nøkkel som skal propageres") {
            it("verdi fra MDC key blir propagert") {
                val response: HttpResponse = httpClient.get("")
                response.headers[mdcKey] shouldBe null
            }
        }

        context("når mdc key propageres som annen header key") {
            val client = HttpClient(mockEngine) {
                install(PropagateFromMDCPlugin) {
                    propagate(mdcKey asHeader "foolias")
                }
            }
            MDC.put(mdcKey, mdcValue)
            it("verdi fra MDC key blir propagert som angitt header key") {
                val response: HttpResponse = client.get("")
                response.headers[mdcKey] shouldBe null
                response.headers["foolias"] shouldBe mdcValue
            }
        }

        context("når mdc key propageres som mdc key og annen header key") {
            val client = HttpClient(mockEngine) {
                install(PropagateFromMDCPlugin) {
                    propagate(mdcKey)
                    propagate(mdcKey asHeader "foolias")
                }
            }
            MDC.put(mdcKey, mdcValue)
            it("verdi fra MDC key blir propagert som angitt header key") {
                val response: HttpResponse = client.get("")
                response.headers[mdcKey] shouldBe mdcValue
                response.headers["foolias"] shouldBe mdcValue
            }
        }
    }
})
