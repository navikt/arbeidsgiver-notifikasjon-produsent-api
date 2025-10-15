package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.IdentityProvider
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthConfig
import org.apache.http.ConnectionClosedException
import javax.net.ssl.SSLHandshakeException

class AltinnTilgangerClient(
    private val baseUrl: String? = null,
    private val authClient: AuthClient = AuthClientImpl(TexasAuthConfig.nais(), IdentityProvider.TOKEN_X),
    private val observer: (orgnr: String, navn: String) -> Unit,
    engine: HttpClientEngine = CIO.create(),
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
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnExceptionIf { _, cause ->
                cause is ConnectionClosedException ||
                        cause is SocketTimeoutException ||
                        cause is SSLHandshakeException ||
                        cause is HttpRequestTimeoutException ||
                        cause is java.nio.channels.UnresolvedAddressException
            }
            delayMillis { 250L }
        }
        expectSuccess = true
    }

    private val targetAudience = "${NaisEnvironment.clusterName}:fager:arbeidsgiver-altinn-tilganger"

    suspend fun hentTilganger(subjectToken: String): AltinnTilganger {
        val token = authClient.exchange(
            target = targetAudience,
            userToken = subjectToken,
        )

        val dto = try {
            httpClient.post {
                url {
                    path("/altinn-tilganger")
                }
                accept(ContentType.Application.Json)
                bearerAuth(token.fold({ it.accessToken }, { throw Exception("Failed to exchange token: ${it.error}") }))
            }.body<AltinnTilgangerClientResponse>()
        } catch (e: Exception) {
            e.rethrowIfCancellation()

            log.error("Failed to fetch tilganger", e)
            return AltinnTilganger(
                harFeil = true,
                tilganger = listOf()
            )
        }

        val alleFlatt = dto.hierarki.flatMap {
            flatten(it) { o ->
                observer(o.orgnr, o.navn)
                AltinnTilgangFlatt(
                    organisasjonsnummer = o.orgnr,
                    navn = o.navn,
                    altinn3Tilganger = o.altinn3Tilganger,
                    altinn2Tilganger = o.altinn2Tilganger,
                )
            }
        }

        return AltinnTilganger(
            harFeil = dto.isError,
            tilganger = alleFlatt.flatMap { org ->
                observer(org.organisasjonsnummer, org.navn)
                (org.altinn2Tilganger + org.altinn3Tilganger).map {
                    AltinnTilgang(
                        orgNr = org.organisasjonsnummer,
                        tilgang = it
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
        val orgnr: String,
        val navn: String,
        val underenheter: List<AltinnTilgang>,

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


/**
 * DTO for altinn 2 og 3 tilganger
 * tilgang: "1234:1" for Altinn 2 og "nav_permitering_og_nedbemanning_innsyn_blabla" for Altinn 3
 */
data class AltinnTilgang(val orgNr: String, val tilgang: String)