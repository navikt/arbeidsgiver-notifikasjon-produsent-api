import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.ktorEngine


class StatusPageTest : DescribeSpec({
    val engine by ktorEngine()

    describe("status page handling") {
        context("when an unexpected error occurs") {
            val ex = RuntimeException("uwotm8?")
            engine.environment.application.routing {
                get("/") {
                    throw ex
                }
            }
            val response = engine.handleRequest(HttpMethod.Get, "/").response

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
    }
})

