package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.netty.handler.codec.http.HttpHeaders.addHeader
import no.nav.arbeidsgiver.notifikasjon.util.get
import no.nav.arbeidsgiver.notifikasjon.util.ktorTestServer

class CorrelationIdTests : DescribeSpec({
    val engine = ktorTestServer()

    describe("correlation id handling") {
        context("when no callid given") {
            val response = engine.get("/internal/alive")
            it("generates callid for us") {
                response.headers[HttpHeaders.XCorrelationId] shouldNot beBlank()
            }
        }

        context("with callid") {
            val callid = "1234"

            context("with header name:") {
                forAll("callid", "CALLID", "call-id") { headerName ->
                    val response = engine.get( "/internal/alive") {
                        addHeader(headerName, callid)
                    }
                    it("it replies with callid: $callid from $headerName") {
                        response.headers[HttpHeaders.XCorrelationId] shouldBe callid
                    }
                }
            }
        }
    }
})
