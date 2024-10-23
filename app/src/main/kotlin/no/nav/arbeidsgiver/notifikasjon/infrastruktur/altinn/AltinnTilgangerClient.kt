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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClientImpl
import org.apache.http.ConnectionClosedException
import javax.net.ssl.SSLHandshakeException

class AltinnTilgangerClient(
    private val baseUrl: String? = null,
    private val tokenXClient: TokenXClient = TokenXClientImpl(),
    private val observer: (orgnr: String, navn: String) -> Unit,
    engine: HttpClientEngine = Apache.create(),
) {

    private val log = logger()

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
        expectSuccess = true
    }

    private val targetAudience = "${NaisEnvironment.clusterName}:fager:arbeidsgiver-altinn-tilganger"

    suspend fun hentTilganger(subjectToken: String): AltinnTilganger {
        val token = try {
            tokenXClient.exchange(
                subjectToken,
                targetAudience
            )
        } catch (e: RuntimeException) {
            log.error("Failed to exchange token", e)
            return AltinnTilganger(
                harFeil = true,
                tilganger = listOf()
            )
        }

        val dto = try {
            httpClient.post {
                url {
                    path("/altinn-tilganger")
                }
                accept(ContentType.Application.Json)
                bearerAuth(token)
            }.body<AltinnTilgangerClientResponse>()
        } catch (e: Exception) {
            log.error("Failed to fetch tilganger", e)
            return AltinnTilganger(
                harFeil = true,
                tilganger = listOf()
            )
        }

        val alleFlatt = dto.hierarki.flatMap {
            flatten(it) { o ->
                observer(o.orgNr, o.name)
                AltinnTilgangFlatt(
                    organisasjonsnummer = o.orgNr,
                    navn = o.name,
                    altinn3Tilganger = o.altinn3Tilganger,
                    altinn2Tilganger = o.altinn2Tilganger,
                )
            }
        }

        return AltinnTilganger(
            harFeil = dto.isError,
            tilganger = alleFlatt.flatMap { org ->
                observer(org.organisasjonsnummer, org.navn)
                org.altinn2Tilganger.map {
                    val (serviceCode, serviceEdition) = it.split(":")
                    AltinnTilgang.Altinn2(
                        orgNr = org.organisasjonsnummer,
                        serviceCode = serviceCode,
                        serviceEdition = serviceEdition
                    )
                } + org.altinn3Tilganger.map {
                    AltinnTilgang.Altinn3(
                        orgNr = org.organisasjonsnummer,
                        ressurs = it
                    )
                }
            }
        )
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class AltinnTilgangerClientResponse(
    val isError: Boolean,
    val hierarki: List<AltinnTilgang>,
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AltinnTilgang(
        val orgNr: String,
        val underenheter: List<AltinnTilgang>,
        val name: String,

        val altinn3Tilganger: Set<String>,
        val altinn2Tilganger: Set<String>,
    )
}

private fun <T> flatten(
    altinnTilgang:AltinnTilgangerClientResponse.AltinnTilgang,
    mapFn: (AltinnTilgangerClientResponse.AltinnTilgang) -> T
): Set<T> {
    val children = altinnTilgang.underenheter.flatMap { flatten(it, mapFn) }
    return setOf(
        mapFn(altinnTilgang)
    ) + children
}

private data class AltinnTilgangFlatt(
    val organisasjonsnummer: String,
    val navn: String,
    val altinn3Tilganger: Set<String>,
    val altinn2Tilganger: Set<String>,
)

data class AltinnTilganger(
    val harFeil: Boolean,
    val tilganger: List<AltinnTilgang>,
)

sealed class AltinnTilgang {
    data class Altinn2(val orgNr: String, val serviceCode: String, val serviceEdition: String) : AltinnTilgang()
    data class Altinn3(val orgNr: String, val ressurs: String) : AltinnTilgang()
}