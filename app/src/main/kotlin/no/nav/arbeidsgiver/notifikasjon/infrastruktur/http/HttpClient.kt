package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson.*
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.HttpClientMetricsFeature
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.PropagateFromMDCPlugin
import java.io.EOFException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLHandshakeException

fun defaultHttpClient(
    configure: HttpClientConfig<CIOEngineConfig>.() -> Unit = {}
) = HttpClient(CIO) {
    expectSuccess = true

    install(ContentNegotiation) {
        jackson()
    }

    install(HttpClientMetricsFeature) {
        registry = Metrics.meterRegistry
    }

    install(PropagateFromMDCPlugin) {
        propagate("x_correlation_id")
    }

    install(HttpRequestRetry) {
        maxRetries = 5
        retryIf { _, res ->
            res.status == HttpStatusCode.ServiceUnavailable ||
                    res.status == HttpStatusCode.GatewayTimeout ||
                    res.status == HttpStatusCode.BadGateway
        }
        retryOnExceptionIf { _, cause ->
            when (cause) {
                is SocketTimeoutException,
                is EOFException,
                is SSLHandshakeException,
                is ClosedReceiveChannelException,
                is HttpRequestTimeoutException -> true

                else -> false
            }
        }

        delayMillis { 250L }
    }

    configure()
}

val httpClientTaggedTimerTimer = ConcurrentHashMap<String, Timer>()

fun withTimer(name: String): Timer =
    httpClientTaggedTimerTimer.computeIfAbsent(name) {
        Timer.builder("http_client")
            .tag("name", it)
            .publishPercentileHistogram()
            .register(Metrics.meterRegistry)
    }