package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.util.concurrent.atomic.AtomicInteger


/**
 * inspired by MicrometerMetrics, but for clients
 */
class HttpClientMetricsFeature internal constructor(
    private val registry: MeterRegistry,
    private val clientName: String,
) {

    private val active = registry.gauge(activeRequestsGaugeName, AtomicInteger(0))

    /**
     * [HttpClientMetricsFeature] configuration that is used during installation
     */
    class Config {
        var clientName: String = "ktor.http.client"
        lateinit var registry: MeterRegistry
    }

    private fun before(httpRequestBuilder: HttpRequestBuilder) {
        active?.incrementAndGet()

        httpRequestBuilder.attributes.put(measureKey, ClientCallMeasure(Timer.start(registry)))
    }

    private fun throwable(context: HttpRequestBuilder, t: Throwable) {
        context.attributes.getOrNull(measureKey)?.apply {
            throwable = t
        }
    }

    private fun throwable(context: HttpClientCall, t: Throwable) {
        context.attributes.getOrNull(measureKey)?.apply {
            throwable = t
        }
    }

    private fun after(context: HttpRequestBuilder) {
        val clientCallMeasure = context.attributes.getOrNull(measureKey)
        if (clientCallMeasure?.throwable != null) {
            // send av request feilet

            active?.decrementAndGet()
            val builder = Timer.builder(requestTimeTimerName).tags(
                listOf(
                    Tag.of("address", context.url.let { "${it.host}:${it.port}" }),
                    Tag.of("method", context.method.value),
                    Tag.of("url", context.attributes[measureKey].url ?: context.url.toString()),
                    Tag.of("status", "n/a"),
                    Tag.of("throwable", clientCallMeasure.throwable!!::class.qualifiedName!! )
                )
            )
            clientCallMeasure.timer.stop(builder.register(registry))
        }
    }

    private fun after(context: HttpClientCall) {
        active?.decrementAndGet()

        val clientCallMeasure = context.attributes.getOrNull(measureKey)
        if (clientCallMeasure != null) {
            val builder = Timer.builder(requestTimeTimerName).tags(
                listOf(
                    Tag.of("address", context.request.url.let { "${it.host}:${it.port}" }),
                    Tag.of("method", context.request.method.value),
                    Tag.of("url", context.attributes[measureKey].url ?: context.request.url.toString()),
                    Tag.of("status", context.response.status.value.toString()),
                    Tag.of("throwable", clientCallMeasure.throwable?.let { it::class.qualifiedName } ?: "n/a")
                )
            )
            clientCallMeasure.timer.stop(builder.register(registry))
        }
    }

    /**
     * Companion object for feature installation
     */
    @Suppress("EXPERIMENTAL_API_USAGE_FUTURE_ERROR")
    companion object Feature : HttpClientFeature<Config, HttpClientMetricsFeature> {
        private var clientName: String = "ktor.http.client"

        val requestTimeTimerName: String
            get() = "$clientName.requests"
        val activeRequestsGaugeName: String
            get() = "$clientName.requests.active"

        private val measureKey = AttributeKey<ClientCallMeasure>("HttpClientMetricsFeature")
        override val key: AttributeKey<HttpClientMetricsFeature> = AttributeKey("HttpClientMetricsFeature")

        override fun prepare(block: Config.() -> Unit): HttpClientMetricsFeature {
            val config = Config().apply(block)
            // validate config?

            return HttpClientMetricsFeature(config.registry, config.clientName)
        }

        override fun install(feature: HttpClientMetricsFeature, scope: HttpClient) {
            clientName = feature.clientName

            scope.requestPipeline.intercept(HttpRequestPipeline.Phases.Send) {
                feature.before(context)
                try {
                    proceed()
                } catch (e: Throwable) {
                    feature.throwable(context, e)
                    throw e
                } finally {
                    feature.after(context)
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Phases.Receive) {
                try {
                    proceed()
                } catch (e: Throwable) {
                    feature.throwable(context, e)
                    throw e
                } finally {
                    feature.after(context)
                }
            }
        }
    }
}

private data class ClientCallMeasure(
    val timer: Timer.Sample,
    var url: String? = null,
    var throwable: Throwable? = null
)