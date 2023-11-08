package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.io.ContentReference
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import java.lang.reflect.Proxy


class StatusPageTests : DescribeSpec({
    val logCalls = mutableMapOf<String, Array<out Any?>>()
    val spiedOnLogger = Proxy.newProxyInstance(
        org.slf4j.Logger::class.java.classLoader,
        arrayOf(org.slf4j.Logger::class.java)
    ) { _, method, args ->
        logCalls[method.name] = args
        null
    } as org.slf4j.Logger

    val engine = ktorBrukerTestServer(
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
                "Error", JsonLocation(ContentReference.unknown(), 42, 42, 42)
            ) {}
            val response = whenExceptionThrown("/some/json/exception", ex)

            it("it returns InternalServerError") {
                response.status() shouldBe HttpStatusCode.InternalServerError
            }
            it("and excludes JsonLocation from log") {
                logCalls["warn"]?.let {
                    it[0] shouldBe "unhandled exception in ktor pipeline: {}"
                    //it[1] shouldBe ex::class.qualifiedName // qualifiedName is null for anonymous object
                    (it[2] as JsonProcessingException).location shouldBe null
                }
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

