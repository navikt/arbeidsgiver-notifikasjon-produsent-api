package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class Subsystem {
    DATABASE
}

object Health {
    val clock: Clock = Clock.SYSTEM

    val meterRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        CollectorRegistry.defaultRegistry,
        clock
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

suspend fun <T> Timer.coRecord(body: suspend () -> T): T {
    val start = Health.clock.monotonicTime()
    try {
        return body()
    } finally {
        val end = Health.clock.monotonicTime()
        this.record(start - end, TimeUnit.NANOSECONDS)
    }
}
