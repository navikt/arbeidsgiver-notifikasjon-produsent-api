package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonProcessingException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.spyk
import io.mockk.verify
import no.nav.arbeidsgiver.notifikasjon.util.ktorTestServer
import org.slf4j.LoggerFactory


class StatusPageTests : DescribeSpec({
    val spiedOnLogger = spyk(LoggerFactory.getLogger("KtorTestApplicationLogger"))
    val engine = ktorTestServer(
        environment = {
            log = spiedOnLogger
        }
    )

    fun whenExceptionThrown(path: String, ex: Exception): TestApplicationResponse {
        engine.environment.application.routing {
            get(path) {
                throw ex
            }
        }
        return engine.handleRequest(HttpMethod.Get, path).response
    }

    describe("status page handling") {
        context("when an unexpected error occurs") {
            val ex = RuntimeException("uwotm8?")
            val response = whenExceptionThrown("/some/runtime/exception", ex)

            it("it returns InternalServerError") {
               response.status() shouldBe HttpStatusCode.InternalServerError
            }
            it("and response has no content") {
                response.content shouldNotContain ex.message!!
            }
            it("and response header contains correlation id") {
                response.headers[HttpHeaders.XCorrelationId] shouldNot beBlank()
            }
        }

        context("when an JsonProcessingException occurs") {
            val ex = object : JsonProcessingException(
                "Error", JsonLocation({}, 42,42,42)
            ) {}
            val response = whenExceptionThrown("/some/json/exception", ex)

            it("it returns InternalServerError") {
                response.status() shouldBe HttpStatusCode.InternalServerError
            }
            it("and excludes JsonLocation from log") {
                verify { spiedOnLogger.warn(
                    any() as String,
                    ex::class.qualifiedName,
                    withArg { jpex:JsonProcessingException ->
                        jpex.location shouldBe null
                    }
                )}
            }
            it("and response does not include exception message") {
                response.content shouldNotContain ex.message!!
            }
            it("and response header contains correlation id") {
                response.headers[HttpHeaders.XCorrelationId] shouldNot beBlank()
            }
        }
    }
})

