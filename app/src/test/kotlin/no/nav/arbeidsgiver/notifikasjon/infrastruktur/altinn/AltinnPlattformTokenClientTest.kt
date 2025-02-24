package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientStub
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenResponse
import java.time.Instant

class AltinnPlattformTokenClientTest : DescribeSpec({
    describe("AltinnPlattformTokenClient") {
        var fakeAltinnToken = JWT.create().withExpiresAt(Instant.now().plusSeconds(1200)).sign(Algorithm.none())

        val altinnReqs = mutableListOf<HttpRequestData>()
        val mockEngine = MockEngine { req ->
            altinnReqs.add(req)
            respond(
                content = ByteReadChannel(fakeAltinnToken),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val authClientReqs = mutableListOf<String>()
        val authClientStub = object : AuthClientStub() {
            override suspend fun token(target: String): TokenResponse {
                authClientReqs.add(target)
                return TokenResponse.Success("maskinportenToken", 42)
            }
        }

        fun newClient() = AltinnPlattformTokenClient(
            altinnBaseUrl = "http://altinn",
            authClient = authClientStub,
            httpClient = HttpClient(mockEngine)
        )

        beforeEach {
            altinnReqs.clear()
            authClientReqs.clear()
        }

        it("utveksler maskinporten token til altinn plattform token") {
            val client = newClient()

            client.token("foo:bar").let {  token ->
                token shouldBe fakeAltinnToken
                authClientReqs shouldBe listOf("foo:bar")
                altinnReqs.map { it.url.toString() } shouldBe listOf("http://altinn/authentication/api/v1/exchange/maskinporten")
            }
            client.token("foo:bar")
            altinnReqs.size shouldBe 1
        }

        it("cacher altinn plattform token for scope") {
            val client = newClient()

            client.token("foo:bar")
            client.token("foo:bar")
            altinnReqs.size shouldBe 1
            client.token("foo:baz")
            altinnReqs.size shouldBe 2
        }

        it("utløpt token hentes på nytt") {
            val client = newClient()
            fakeAltinnToken = JWT.create().withExpiresAt(Instant.now()).sign(Algorithm.none())

            client.token("foo:bar")
            client.token("foo:bar")
            altinnReqs.size shouldBe 2
            client.token("foo:baz")
            altinnReqs.size shouldBe 3
        }
    }

})