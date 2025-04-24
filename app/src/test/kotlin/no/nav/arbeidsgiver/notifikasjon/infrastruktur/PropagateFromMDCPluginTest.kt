package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.slf4j.MDC
import kotlin.test.Test
import kotlin.test.assertEquals


@Suppress("UastIncorrectHttpHeaderInspection")
class PropagateFromMDCPluginTest {
    private val mdcKey = "foo"
    private val mdcValue = "42"
    private val mockEngine = MockEngine { request ->
        respond(
            content = ByteReadChannel(""),
            status = HttpStatusCode.OK,
            headers = request.headers
        )
    }
    private val httpClient = HttpClient(mockEngine) {
        install(PropagateFromMDCPlugin) {
            propagate(mdcKey)
        }
    }

    @BeforeEach
    fun setUp() {
        MDC.clear()
    }

    @Test
    fun `MDC inneholder nøkkel som skal propageres`() = runTest {
        MDC.put(mdcKey, mdcValue)
        // verdi fra MDC key blir propagert
        val response: HttpResponse = httpClient.get("")
        assertEquals(mdcValue, response.headers[mdcKey])
    }

    @Test
    fun `MDC inneholder ikke nøkkel som skal propageres`() = runTest {
        // verdi fra MDC key blir propagert
        val response: HttpResponse = httpClient.get("")
        assertEquals(null, response.headers[mdcKey])
    }

    @Test
    fun `når mdc key propageres som annen header key`() = runTest {
        val client = HttpClient(mockEngine) {
            install(PropagateFromMDCPlugin) {
                propagate(mdcKey asHeader "foolias")
            }
        }
        MDC.put(mdcKey, mdcValue)
        // verdi fra MDC key blir propagert som angitt header key
        val response: HttpResponse = client.get("")
        assertEquals(null, response.headers[mdcKey])
        assertEquals(mdcValue, response.headers["foolias"])
    }

    @Test
    fun `når mdc key propageres som mdc key og annen header key`() = runTest {
        val client = HttpClient(mockEngine) {
            install(PropagateFromMDCPlugin) {
                propagate(mdcKey)
                propagate(mdcKey asHeader "foolias")
            }
        }
        MDC.put(mdcKey, mdcValue)
        // verdi fra MDC key blir propagert som angitt header key
        val response: HttpResponse = client.get("")
        assertEquals(mdcValue, response.headers[mdcKey])
        assertEquals(mdcValue, response.headers["foolias"])
    }
}
