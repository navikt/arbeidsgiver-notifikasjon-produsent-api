package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

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

fun <T: ExecutorService> T.produceMetrics(name: String): T {
    ExecutorServiceMetrics(this, name, emptyList())
        .bindTo(Health.meterRegistry);
    return this
}


private val timerRegistry = ConcurrentHashMap<Pair<String, Set<Pair<String, String>>>, Timer>()

fun getTimer(
    name: String,
    tags: Set<Pair<String, String>>,
    description: String,
): Timer {
    return timerRegistry.computeIfAbsent(Pair(name, tags)) {
        val tagsArray =
            tags.toList()
                .flatMap { (tagName, tagValue) -> listOf(tagName, tagValue) }
                .toTypedArray()
        Timer.builder(name)
            .tags(*tagsArray)
            .description(description)
            .publishPercentiles(0.5, 0.8, 0.9, 0.99)
            .register(Health.meterRegistry)
    }
}