package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


/**
 * inspired by [io.ktor.server.metrics.micrometer.MicrometerMetrics], but for clients.
 * this feature/plugin generates the following metrics:
 * (x = ktor.http.client, but can be overridden)
 *
 * x.requests: a timer for measuring the time of each request. This metric provides a set of tags for monitoring request data, including http method, path, status
 * x.activeRequests: a gauge for the number of active requests
 */
class HttpClientMetrics internal constructor(
    private val registry: MeterRegistry,
    private val clientName: String,
) {
    class Config {
        var clientName: String = "ktor.http.client"
        lateinit var registry: MeterRegistry

        internal fun isRegistryInitialized() = this::registry.isInitialized
    }

    private fun activeRequestsPerClientName() =
        activeRequests.getOrPut(clientName) {
            AtomicInteger(0).also { activeRequests ->
                Metrics.meterRegistry.gauge(
                    activeRequestsGaugeName,
                    Tags.of(Tag.of("instance", "${hashCode()}")),
                    activeRequests
                )
            }
        }

    private fun before(context: HttpRequestBuilder) {
        activeRequestsPerClientName()?.incrementAndGet()
        context.attributes.put(measureKey, ClientCallMeasure(Timer.start(registry), context.url.encodedPath))
    }

    private fun after(call: HttpClientCall, context: HttpRequestBuilder) {
        activeRequestsPerClientName()?.decrementAndGet()
        val clientCallMeasure = call.attributes.getOrNull(measureKey)
        if (clientCallMeasure != null) {
            val builder = Timer.builder(requestTimeTimerName).tags(
                listOf(
                    Tag.of("method", call.request.method.value),
                    Tag.of("url", context.urlTagValue()),
                    Tag.of("status", call.response.status.value.toString()),
                )
            )
            clientCallMeasure.timer.stop(builder.register(registry))
        }
    }

    companion object Plugin : HttpClientPlugin<Config, HttpClientMetrics> {
        private var clientName: String = "ktor.http.client"

        val requestTimeTimerName: String
            get() = "$clientName.requests"

        val activeRequestsGaugeName : String
            get() = "$clientName.activeRequests"

        val activeRequests = ConcurrentHashMap<String, AtomicInteger>()

        private val measureKey = AttributeKey<ClientCallMeasure>("HttpClientMetricsFeature")
        override val key: AttributeKey<HttpClientMetrics> = AttributeKey("HttpClientMetricsFeature")

        override fun prepare(block: Config.() -> Unit): HttpClientMetrics =
            Config().apply(block).let {
                if (!it.isRegistryInitialized()) {
                    throw IllegalArgumentException(
                        "Meter registry is missing. Please initialize the field 'registry'"
                    )
                }
                HttpClientMetrics(it.registry, it.clientName)
            }

        override fun install(plugin: HttpClientMetrics, scope: HttpClient) {
            clientName = plugin.clientName

            scope.plugin(HttpSend).intercept { context ->
                plugin.before(context)

                val origin = execute(context)
                plugin.after(origin, context)

                origin
            }
        }
    }

    private fun HttpRequestBuilder.urlTagValue() =
        "${url.let { "${it.protocol.name}://${it.host}:${it.port}" }}${attributes[measureKey].path}"
}

private data class ClientCallMeasure(
    val timer: Timer.Sample,
    val path: String,
)
