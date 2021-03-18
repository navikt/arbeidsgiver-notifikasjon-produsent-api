package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.ktor.server.testing.*

val objectMapper = jacksonObjectMapper()

fun TestApplicationRequest.setJsonBody(body: Any) {
    setBody(objectMapper.writeValueAsString(body))
}

inline fun <reified T> TestApplicationResponse.getTypedContent(name: String): T{
    if (this.content == null) {
        throw NullPointerException("content is null. status:${status()}")
    }
    val tree = objectMapper.readTree(this.content!!)
    val node = tree.get("data").get(name)
    return objectMapper.convertValue(node)
}

class GraphQLTests : DescribeSpec({
    val engine by ktorEngine()

    describe("POST /api/graphql") {
        lateinit var response: TestApplicationResponse
        lateinit var query: String

        beforeEach {
            response = engine.handleRequest(HttpMethod.Post, "/api/graphql") {
                addHeader(HttpHeaders.Authorization, "Bearer $tokenDingsToken")
                addHeader(HttpHeaders.ContentType, "application/json")
                addHeader(HttpHeaders.Accept, "application/json")
                setJsonBody(GraphQLRequest(
                        query = query
                ))
            }.response
        }

        context("Query.world") {
            query = """
                { world { greeting } }
            """.trimIndent()

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("it returns greeting") {
                response.getTypedContent<World>("world").greeting shouldBe "Hello world!"
            }
        }

        context("Query.addition") {
            query = """
                { addition(a: 2, b: 2) { sum } }
            """.trimIndent()

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("it returns correct sum") {
                response.getTypedContent<Addition>("addition").sum shouldBe 4
            }
        }

    }
})

