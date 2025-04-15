package no.nav.arbeidsgiver.notifikasjon

import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals

class CorrelationIdTest {

    @Test
    fun `should generate callid when not given`() = ktorBrukerTestServer {
        val response = client.get("/internal/alive")

        assertFalse(response.headers[HttpHeaders.XCorrelationId].isNullOrBlank())
    }

    @Test
    fun `should generate callid when given`() = ktorBrukerTestServer {
        listOf(
            "callid", "CALLID", "call-id"
        ).forEach { headerName ->
            val callid = "1234"
            val response = client.get("/internal/alive") {
                header(headerName, callid)
            }
            assertEquals(callid, response.headers[HttpHeaders.XCorrelationId])
        }
    }
}
