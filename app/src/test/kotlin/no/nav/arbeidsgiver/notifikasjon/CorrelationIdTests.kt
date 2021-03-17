import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.ktorEngine

class CorrelationIdTest : DescribeSpec({
    val engine by ktorEngine()

    fun get(uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationResponse
            = engine.handleRequest(HttpMethod.Get, uri, setup).response

    fun getWithHeader(header: Pair<String, String>? = null): TestApplicationResponse {
        return get("/internal/alive") {
            if (header != null) {
                this.addHeader(header.first, header.second)
            }
        }
    }

    describe("correlation id handling") {
        context("when no callid given") {
            val response = getWithHeader()
            it("generates callid for us") {
                response.headers[HttpHeaders.XCorrelationId] shouldNot beBlank()
            }
        }

        context("with callid") {
            val callid = "1234"

            context("with header name:") {
                forAll("callid", "CALLID", "call-id") { headerName ->
                    val response = getWithHeader(headerName to callid)
                    it("it replies with callid: $callid from $headerName") {
                        response.headers[HttpHeaders.XCorrelationId] shouldBe callid
                    }
                }
            }
        }
    }
})
