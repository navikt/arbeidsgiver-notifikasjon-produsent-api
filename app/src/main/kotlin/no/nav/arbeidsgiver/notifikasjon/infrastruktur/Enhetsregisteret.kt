package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import org.apache.http.ConnectionClosedException

interface Enhetsregisteret {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Underenhet(
        val organisasjonsnummer: String,
        val navn: String,
    )

    suspend fun hentUnderenhet(orgnr: String): Underenhet
}

fun enhetsregisterFactory() =
    basedOnEnv(
        prod = { EnhetsregisteretImpl() },
        other = { EnhetsregisteretDevImpl() }
    )

class EnhetsregisteretDevImpl : Enhetsregisteret {
    override suspend fun hentUnderenhet(orgnr: String) =
        Enhetsregisteret.Underenhet(
            organisasjonsnummer = orgnr,
            navn = ""
        )
}

class EnhetsregisteretImpl(
    private val baseUrl: String = "https://data.brreg.no"
) : Enhetsregisteret {
    private val log = logger()

    private val timer = Metrics.meterRegistry.timer("brreg_hent_organisasjon")

    private val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson()
        }
        install(PropagateFromMDCPlugin) {
            propagate("x_correlation_id")
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            staticPath = "/enhetsregisteret/api/underenheter/"
        }
        install(HttpRequestRetry) {
            maxRetries = 2
            retryOnExceptionIf { _, cause ->
                cause is ConnectionClosedException
                //cause is NoHttpResponseException ||
                //cause is SocketException ||
                //cause is SSLHandshakeException
            }
            delayMillis { 100L }
        }
        expectSuccess = false
        engine {
            socketTimeout = 1000
            connectTimeout = 1000
            connectionRequestTimeout = 2000
        }
    }

    override suspend fun hentUnderenhet(orgnr: String) = timer.coRecord {
        val response: HttpResponse = try {
            httpClient.get("$baseUrl/enhetsregisteret/api/underenheter/$orgnr")
        } catch (e: RuntimeException) {
            log.warn("kall mot $baseUrl feilet", e)
            return@coRecord Enhetsregisteret.Underenhet(orgnr, "")
        }
        if (response.status.isSuccess()) {
            try {
                response.body()
            } catch (e: RuntimeException) {
                log.warn("feil ved deserializing av response fra enhetsregisteret", e)
                Enhetsregisteret.Underenhet(orgnr, "")
            }
        } else {
            log.warn("kunne ikke finne navn for virksomhet. kall til brreg feilet: ${response.status} ${response.bodyAsText()}")
            Enhetsregisteret.Underenhet(orgnr, "")
        }
    }
}
