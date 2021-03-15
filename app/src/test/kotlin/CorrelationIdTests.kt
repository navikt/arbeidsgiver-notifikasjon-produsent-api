import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.GivenScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.module


class CorrelationIdTests : BehaviorSpec({
    lateinit var engine: TestApplicationEngine

    fun responseOfGet(uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationResponse
        = engine.handleRequest(HttpMethod.Get, uri, setup).response


    aroundTest { test ->
        engine = TestApplicationEngine(createTestEnvironment())
        engine.start()
        try {
            engine.run {
                application.module()
                val (arg, body) = test
                body.invoke(arg)
            }
        } finally {
            engine.stop(0L, 0L)
        }
    }

    given("no correlation id") {
        `when`("when calling endpoing") {
            val response = responseOfGet("/internal/alive")
            then("I should receive one") {
                response.headers[HttpHeaders.XCorrelationId] shouldNot beBlank()
            }
        }
    }

    given("a correlation id") {
        val id = "1234"

        suspend fun GivenScope.worksFor(headerName: String) {
            `when`("passed as $headerName") {
                val response = responseOfGet("/internal/alive") {
                    addHeader(headerName, id)
                }
                then("it should be returned") {
                    response.headers[HttpHeaders.XCorrelationId] shouldBe id
                }
            }
        }

        worksFor(HttpHeaders.XCorrelationId)
        worksFor("callid")
        worksFor("CALLID")
        worksFor("CALL-ID")

        `when`("passed as foobar") {
            val response = responseOfGet("/internal/alive") {
                addHeader("foobar", id)
            }
            then("a random one should be generated") {
                response.headers[HttpHeaders.XCorrelationId] shouldNotBe id
            }
        }
    }
})

