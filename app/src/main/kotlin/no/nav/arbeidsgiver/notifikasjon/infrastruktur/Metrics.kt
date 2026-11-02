package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

object Metrics {
    val clock: Clock = Clock.SYSTEM

    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

/**
 * Måler [body] med denne timeren. Feiler [body], registreres tiden i tillegg på en timer med samme
 * navn og tags, pluss taggen `throwable`.
 *
 * Feil-timeren bygges på nytt fra navn og tags, så timerens distribusjonskonfig blir ikke med.
 * Sett [publishPercentileHistogram] til samme verdi som timeren ble registrert med. Ellers får samme
 * metrikknavn både histogram og summary, og Prometheus-scrape feiler med ClassCastException.
 */
suspend fun <T> Timer.coRecord(
    publishPercentileHistogram: Boolean = false,
    body: suspend () -> T,
): T {
    val start = Metrics.clock.monotonicTime()
    try {
        return body()
    } catch (t: Throwable) {
        Timer.builder(this.id.name)
            .tags(this.id.tags)
            .tag("throwable", t.javaClass.canonicalName)
            .publishPercentileHistogram(publishPercentileHistogram)
            .register(Metrics.meterRegistry)
            .record(Metrics.clock.monotonicTime() - start, TimeUnit.NANOSECONDS)
        throw t
    } finally {
        val end = Metrics.clock.monotonicTime()
        this.record(end - start, TimeUnit.NANOSECONDS)
    }
}

fun <T: ExecutorService> T.produceMetrics(name: String): T {
    ExecutorServiceMetrics(this, name, emptyList())
        .bindTo(Metrics.meterRegistry)
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
            .register(Metrics.meterRegistry)
    }
}