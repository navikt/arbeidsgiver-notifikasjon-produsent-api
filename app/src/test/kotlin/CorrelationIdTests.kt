import io.kotest.core.TestConfiguration
import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.module
import kotlin.reflect.KProperty


class TestApplicationEngineDelegate(context: TestConfiguration) {
    lateinit var engine: TestApplicationEngine

    init {
        context.aroundTest { test ->
            engine = TestApplicationEngine(createTestEnvironment())
            engine.start()
            try {
                engine.run {
                    application.module()
                    val (arg, body) = test
                    body.invoke(arg)
                }
            } finally {
                engine.stop(0L, 0L)
            }
        }
    }
    operator fun getValue(thisRef: Any?, property: KProperty<*>): TestApplicationEngine {
        return engine
    }
}

fun TestConfiguration.ktorEngine() = TestApplicationEngineDelegate(this)

class CorrelationIdTest : DescribeSpec({
    val engine by ktorEngine()

    fun get(uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationResponse
            = engine.handleRequest(HttpMethod.Get, uri, setup).response

    fun getWithHeader(header: Pair<String, String>? = null): String {
        return get("/internal/alive") {
            if (header != null) {
                this.addHeader(header.first, header.second)
            }
        }
            .headers[HttpHeaders.XCorrelationId]!!
    }

    describe("correlation id handling") {
        context("when no callid given") {
            it("generates callid for us") {
               getWithHeader() shouldNot beBlank()
            }
        }

        context("with callid") {
            val callid = "1234"

            context("with header name:") {
                forAll("callid", "CALLID", "call-id") { headerName ->
                    it("it replies with callid: $callid from $headerName") {
                        getWithHeader(headerName to callid) shouldBe callid
                    }
                }
            }
        }
    }
})

