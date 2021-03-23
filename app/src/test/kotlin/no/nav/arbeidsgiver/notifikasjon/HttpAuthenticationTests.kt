package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*

class HttpAuthenticationTests: DescribeSpec({
    val engine by ktorEngine()

    describe("When calling graphql-endpoint without bearer token") {
        val result = engine.produsentGet("/api/ide")
        it("returns 401") {
            result.status() shouldBe HttpStatusCode.Unauthorized
        }
    }
})