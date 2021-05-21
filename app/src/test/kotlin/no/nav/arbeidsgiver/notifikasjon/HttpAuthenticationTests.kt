package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.mockk

class HttpAuthenticationTests: DescribeSpec({
    val engine = ktorTestServer()

    describe("When calling graphql-endpoint without bearer token") {
        val result = engine.post("/api/graphql", host = PRODUSENT_HOST)
        it("returns 401") {
            result.status() shouldBe HttpStatusCode.Unauthorized
        }
    }
})