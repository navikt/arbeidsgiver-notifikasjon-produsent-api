package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.TestConfiguration
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