package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.io.ContentReference
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class StatusPageTest {
    val logCalls = mutableMapOf<String, Array<out Any?>>()
    val spiedOnLogger = Proxy.newProxyInstance(
        org.slf4j.Logger::class.java.classLoader,
        arrayOf(org.slf4j.Logger::class.java)
    ) { _, method, args ->
        logCalls[method.name] = args
        null
    } as org.slf4j.Logger

    fun whenExceptionThrown(
        path: String,
        ex: Exception,
        testBlock: suspend ApplicationTestBuilder.(response: HttpResponse) -> Unit
    ) = ktorBrukerTestServer(envBlock = {
        log = spiedOnLogger
    }) {
        application {
            routing {
                get(path) {
                    throw ex
                }
            }
        }
        val response = client.get(path)
        testBlock(response)
    }

    @Test
    fun `status page handling`() {
        val ex = RuntimeException("uwotm8?")
        whenExceptionThrown("/some/runtime/exception", RuntimeException("uwotm8?")) { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertFalse(response.bodyAsText().contains(ex.message!!))
            assertFalse(response.headers[HttpHeaders.XCorrelationId].isNullOrBlank())
        }
    }

    @Test
    fun `when an JsonProcessingException occurs`() {
        val ex = object : JsonProcessingException(
            "Error", JsonLocation(ContentReference.unknown(), 42, 42, 42)
        ) {}
        whenExceptionThrown("/some/json/exception", ex) { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)

            logCalls["warn"]?.let {
                assertEquals("unhandled exception in ktor pipeline: {}", it[0])
                //it[1] shouldBe ex::class.qualifiedName // qualifiedName is null for anonymous object
                assertEquals(null, (it[2] as JsonProcessingException).location)
            }
            assertFalse(response.bodyAsText().contains(ex.message!!))
            assertFalse(response.headers[HttpHeaders.XCorrelationId].isNullOrBlank())
        }
    }
}

