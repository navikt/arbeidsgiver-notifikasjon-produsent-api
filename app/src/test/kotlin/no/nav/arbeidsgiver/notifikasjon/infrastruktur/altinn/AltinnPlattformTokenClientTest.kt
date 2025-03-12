package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientStub
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenResponse
import java.time.Instant

class AltinnPlattformTokenClientTest : DescribeSpec({
    describe("AltinnPlattformTokenClient") {
        val authClientReqs = mutableListOf<String>()
        val authClientStub = object : AuthClientStub() {
            override suspend fun token(target: String): TokenResponse {
                authClientReqs.add(target)
                return TokenResponse.Success("maskinportenToken", 42)
            }
        }

        fun newClient(altinnToken: String) = AltinnPlattformTokenClientImpl(
            altinnBaseUrl = "http://altinn",
            authClient = authClientStub,
            httpClient = HttpClient(MockEngine { _ ->
                respondOk("\"$altinnToken\"")
            })
        )

        beforeEach {
            authClientReqs.clear()
        }

        it("utveksler maskinporten token til altinn plattform token") {
            val fakeAltinnToken = JWT.create().withExpiresAt(Instant.now().plusSeconds(1200)).sign(Algorithm.none())
            val client = newClient(fakeAltinnToken)

            client.token("foo:bar").let { token ->
                token shouldBe fakeAltinnToken
                authClientReqs shouldBe listOf("foo:bar")
                client.mock().requestHistory.map { it.url.toString() } shouldBe listOf("http://altinn/authentication/api/v1/exchange/maskinporten")
            }
            client.token("foo:bar")
            client.mock().requestHistory.size shouldBe 1
        }

        it("cacher altinn plattform token for scope") {
            val client = newClient(JWT.create().withExpiresAt(Instant.now().plusSeconds(1200)).sign(Algorithm.none()))

            client.token("foo:bar")
            client.token("foo:bar")
            (client.httpClient.engine as MockEngine).requestHistory.size shouldBe 1
            client.token("foo:baz")
            client.mock().requestHistory.size shouldBe 2
        }

        it("utløpt token hentes på nytt") {
            val client = newClient(JWT.create().withExpiresAt(Instant.now()).sign(Algorithm.none()))

            client.token("foo:bar")
            client.token("foo:bar")
            client.mock().requestHistory.size shouldBe 2
            client.token("foo:baz")
            client.mock().requestHistory.size shouldBe 3
        }
    }

})

private fun AltinnPlattformTokenClientImpl.mock() = httpClient.engine as MockEngine