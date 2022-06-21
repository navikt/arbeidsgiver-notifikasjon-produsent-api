package no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.HttpClientMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.PropagateFromMDCFeature

object AzureClient {
    private val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson()
        }
        install(PropagateFromMDCFeature) {
            propagate("x_correlation_id")
        }
        install(HttpClientMetrics) {
            registry = Metrics.meterRegistry
        }
    }

    suspend fun fetchToken(targetApp: String, clientAssertion: String): TokenResponse {
        return withContext(Dispatchers.IO) {
            val urlParameters = ParametersBuilder().apply {
                append("tenant", AzureEnv.tenantId)
                append("client_id", AzureEnv.clientId)
                append("scope", "api://$targetApp/.default")
                append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                append("client_assertion", clientAssertion)
                append("grant_type", "client_credentials")
            }.build()

            httpClient.post(AzureEnv.openidTokenEndpoint) {
                setBody(TextContent(urlParameters.formUrlEncode(), ContentType.Application.FormUrlEncoded))
            }.body()
        }
    }
}

data class TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String,
    @JsonProperty("expires_in") val expiresIn: Int,
    @JsonProperty("ext_expires_in") val extExpiresIn: Int
)