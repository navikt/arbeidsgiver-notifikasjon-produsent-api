package no.nav.arbeidsgiver.notifikasjon

import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.util.ktorProdusentTestServer
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpAuthenticationTest {
    @Test
    fun `When calling graphql-endpoint without bearer token`() = ktorProdusentTestServer {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/api/graphql").status
            )
        }
}