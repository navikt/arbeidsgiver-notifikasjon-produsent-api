package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientStub
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenResponse
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AltinnPlattformTokenClientTest {
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

    @BeforeEach
    fun setUp() {
        authClientReqs.clear()
    }

    @Test
    fun `utveksler maskinporten token til altinn plattform token`() = runTest {
        val fakeAltinnToken = JWT.create().withExpiresAt(Instant.now().plusSeconds(1200)).sign(Algorithm.none())
        val client = newClient(fakeAltinnToken)

        client.token("foo:bar").let { token ->
            assertEquals(fakeAltinnToken, token)
            assertEquals(listOf("foo:bar"), authClientReqs)
            assertEquals(
                listOf("http://altinn/authentication/api/v1/exchange/maskinporten"),
                client.mock().requestHistory.map { it.url.toString() })
        }
        client.token("foo:bar")
        assertEquals(1, client.mock().requestHistory.size)
    }

    @Test
    fun `cacher altinn plattform token for scope`() = runTest {
        val client = newClient(JWT.create().withExpiresAt(Instant.now().plusSeconds(1200)).sign(Algorithm.none()))

        client.token("foo:bar")
        client.token("foo:bar")
        assertEquals(1, (client.httpClient.engine as MockEngine).requestHistory.size)
        client.token("foo:baz")
        assertEquals(2, client.mock().requestHistory.size)
    }

    @Test
    fun `utløpt token hentes på nytt`() = runTest {
        val client = newClient(JWT.create().withExpiresAt(Instant.now()).sign(Algorithm.none()))

        client.token("foo:bar")
        client.token("foo:bar")
        assertEquals(2, client.mock().requestHistory.size)
        client.token("foo:baz")
        assertEquals(3, client.mock().requestHistory.size)
    }

}

private fun AltinnPlattformTokenClientImpl.mock() = httpClient.engine as MockEngine