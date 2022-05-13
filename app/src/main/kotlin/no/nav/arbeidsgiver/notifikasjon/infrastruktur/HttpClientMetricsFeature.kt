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
    val clientName: String,
) {

    private val active = registry.gauge(activeRequestsGaugeName, AtomicInteger(0))

    /**
     * [HttpClientMetricsFeature] configuration that is used during installation
     */
    class Config {
        var clientName: String = "ktor.http.client"
        lateinit var registry: MeterRegistry
    }

    private fun ClientCallMeasure.recordDuration(context: HttpClientCall) {
        timer.stop(
            Timer.builder(requestTimeTimerName)
                .addDefaultTags(context, throwable)
                .register(registry)
        )
    }

    private fun Timer.Builder.addDefaultTags(context: HttpClientCall, throwable: Throwable?): Timer.Builder {
        val url = context.attributes[measureKey].url ?: context.request.url.toString()
        tags(
            listOf(
                Tag.of("address", context.request.url.let { "${it.host}:${it.port}" }),
                Tag.of("method", context.request.method.value),
                Tag.of("url", url),
                Tag.of("status", context.response.status.value.toString()),
                Tag.of("throwable", throwable?.let { it::class.qualifiedName } ?: "n/a")
            )
        )
        return this
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

    private fun after(context: HttpClientCall) {
        active?.decrementAndGet()

        context.attributes.getOrNull(measureKey)?.recordDuration(context)
    }

    /**
     * Companion object for feature installation
     */
    @Suppress("EXPERIMENTAL_API_USAGE_FUTURE_ERROR")
    companion object Feature : HttpClientFeature<Config, HttpClientMetricsFeature> {
        private lateinit var clientName: String

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