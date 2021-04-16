package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.ConcurrentHashMap

enum class Subsystem {
    DATABASE
}

object Health {
    val meterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        CollectorRegistry.defaultRegistry,
        Clock.SYSTEM
    )

    val subsystemAlive = ConcurrentHashMap(mapOf(
        Subsystem.DATABASE to true
    ))

    val alive
        get() = subsystemAlive.all { it.value }

    val subsystemReady = ConcurrentHashMap(mapOf(
        Subsystem.DATABASE to false
    ))

    val ready
        get() = subsystemReady.all { it.value }
}
