package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import ch.qos.logback.core.util.OptionHelper.getEnv
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timer

enum class Subsystem {
    DATABASE
}

object Health {
    private val log = logger()

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

    private val terminatingAtomic = AtomicBoolean(false)

    val terminating: Boolean
        get() = terminatingAtomic.get()

    init {
        val shutdownTimeout = basedOnEnv(
            prod = { Duration.ofSeconds(20) },
            dev = { Duration.ofSeconds(20) },
            other = { Duration.ofMillis(0) },
        )

        Runtime.getRuntime().addShutdownHook(object: Thread() {
            override fun run() {
                terminatingAtomic.set(true)
                log.info("shutdown signal received")
                try {
                    sleep(shutdownTimeout.toMillis())
                } catch (e: Exception) {
                }
            }
        })
    }
}

suspend fun <T> Timer.coRecord(body: suspend () -> T): T {
    val start = Health.clock.monotonicTime()
    try {
        return body()
    } finally {
        val end = Health.clock.monotonicTime()
        this.record(end - start, TimeUnit.NANOSECONDS)
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