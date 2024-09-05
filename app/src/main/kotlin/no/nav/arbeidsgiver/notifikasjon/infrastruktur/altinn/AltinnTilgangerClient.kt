package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.HttpClientMetricsFeature
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.PropagateFromMDCPlugin
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClientImpl
import org.apache.http.ConnectionClosedException
import javax.net.ssl.SSLHandshakeException

class AltinnTilgangerClient(
    private val baseUrl: String? = null,
    private val tokenXClient: TokenXClient = TokenXClientImpl(),
    engine: HttpClientEngine = Apache.create(),
) {

    private val httpClient = HttpClient(engine) {
        defaultRequest {
            url(baseUrl ?: "http://arbeidsgiver-altinn-tilganger")
        }
        install(ContentNegotiation) {
            jackson()
        }
        install(PropagateFromMDCPlugin) {
            propagate("x_correlation_id")
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnExceptionIf { _, cause ->
                cause is ConnectionClosedException ||
                        cause is SocketTimeoutException ||
                        cause is SSLHandshakeException
            }
            delayMillis { 250L }
        }
    }

    private val targetAudience = "${NaisEnvironment.clusterName}:fager:arbeidsgiver-altinn-tilganger"

    // TODO: ikke bruk BrukerModel typen her, lag egne DTOer for denne klienten og konverter til BrukerModel i tjenesten
    suspend fun hentTilganger(subjectToken: String): Tilganger {
        val dto = httpClient.post {
            url {
                path("/altinn-tilganger")
            }
            accept(ContentType.Application.Json)
            bearerAuth(
                tokenXClient.exchange(
                    subjectToken,
                    targetAudience
                )
            )
        }.body<AltinnTilgangerResponse>()

        return Tilganger(
            harFeil = dto.isError,
            tjenestetilganger = dto.orgNrTilTilganger.flatMap { (orgNr, tilganger) ->
                tilganger.map { tilgang ->
                    val (code, edition) = tilgang.split(":").let {
                        it.first() to it.getOrElse(1) { "" }
                    }
                    BrukerModel.Tilgang.Altinn(orgNr, code, edition)
                }
            }
        )
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AltinnTilgangerResponse(
    val isError: Boolean,
    val orgNrTilTilganger: Map<String, List<String>>,
)