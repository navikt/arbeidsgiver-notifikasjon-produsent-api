package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.util.get
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer

class CorrelationIdTests : DescribeSpec({
    val engine = ktorBrukerTestServer()

    describe("correlation id handling") {
        context("when no callid given") {
            val response = engine.get("/internal/alive")
            it("generates callid for us") {
                response.headers[HttpHeaders.XCorrelationId] shouldNot beBlank()
            }
        }

        context("with callid") {
            val callid = "1234"

            context("replies with $callid with header name") {
                withData("callid", "CALLID", "call-id") { headerName ->
                    val response = engine.get("/internal/alive") {
                        addHeader(headerName, callid)
                    }
                    response.headers[HttpHeaders.XCorrelationId] shouldBe callid
                }
            }
        }
    }
})
