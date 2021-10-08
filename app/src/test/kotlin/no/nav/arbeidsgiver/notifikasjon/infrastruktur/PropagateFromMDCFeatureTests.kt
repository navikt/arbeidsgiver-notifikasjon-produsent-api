package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.beBlank
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import org.slf4j.MDC


class PropagateFromMDCFeatureTests : DescribeSpec({
    describe("PropagateFromMDCFeature") {
        val key = "foo"
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    key to listOf(request.headers[key]?:"")
                )
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(PropagateFromMDCFeature) {
                propagate(key)
            }
        }

        afterEach {
            MDC.clear()
        }

        context("MDC inneholder nøkkel som skal propageres") {
            MDC.put(key, "42")
            it("verdi fra MDC key blir propagert") {
                val response: HttpResponse = httpClient.get()
                response.headers[key] shouldBe "42"
            }
        }

        context("MDC inneholder ikke nøkkel som skal propageres") {
            it("verdi fra MDC key blir propagert") {
                val response: HttpResponse = httpClient.get()
                response.headers[key] should beBlank()
            }
        }
    }
})
