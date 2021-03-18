package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonProcessingException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldNotContain
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.verify


class StatusPageTest : DescribeSpec({
    val engine by ktorEngine()

    describe("status page handling") {
        lateinit var ex: Throwable
        lateinit var response: TestApplicationResponse

        beforeEach {
            engine.environment.application.routing {
                get("/") {
                    throw ex
                }
            }
            response = engine.handleRequest(HttpMethod.Get, "/").response
        }

        context("when an unexpected error occurs") {
            ex = RuntimeException("uwotm8?")

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
            ex = object : JsonProcessingException(
                "Error", JsonLocation({}, 42,42,42)
            ) {}

            it("it returns InternalServerError") {
                response.status() shouldBe HttpStatusCode.InternalServerError
            }
            it("and excludes JsonLocation from log") {
                verify { engine.application.log.warn(
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

