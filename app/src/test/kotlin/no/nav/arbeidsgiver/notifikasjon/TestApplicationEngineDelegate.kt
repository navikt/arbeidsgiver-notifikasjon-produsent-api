package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.TestConfiguration
import io.ktor.http.*
import io.ktor.server.testing.*
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

fun TestApplicationEngine.get(uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationResponse =
    this.handleRequest(HttpMethod.Get, uri, setup).response

fun TestApplicationEngine.getWithHeader(uri: String, vararg headers: Pair<String, String>): TestApplicationResponse {
    return this.get(uri) {
        headers.forEach {
            addHeader(it.first, it.second)
        }
    }
}

