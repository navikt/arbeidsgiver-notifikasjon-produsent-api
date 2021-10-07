package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.slf4j.MDC
import java.time.Instant
import java.util.*

interface TokenExchangeClient {
    suspend fun exchangeToken(subjectToken: String, targetAudience: String): String
}

class TokenExchangeClientImpl(
    private val wellKnownUrl: String = System.getenv("TOKEN_X_WELL_KNOWN_URL"),
    private val clientId: String = System.getenv("TOKEN_X_CLIENT_ID"),
    private val privateJwk: RSAKey = RSAKey.parse(System.getenv("TOKEN_X_PRIVATE_JWK")),
): TokenExchangeClient {

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        defaultRequest {
            MDC.get("x_correlation_id")?.let {
                header("x_correlation_id", it)
            }
        }
    }

    private val tokenEndpoint: String = runBlocking {
        httpClient.get<JsonNode>(wellKnownUrl)["token_endpoint"].asText()
    }

    private val signingAlgorithm = Algorithm.RSA256(null, privateJwk.toRSAPrivateKey())

    override suspend fun exchangeToken(subjectToken: String, targetAudience: String): String {
        val exchangeResponse = httpClient.submitForm<ExchangeResponse>(
            tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                append("subject_token_type", "urn:ietf:params:oauth:token-type:jwt")
                append("subject_token", subjectToken)
                append("client_assertion", createClientAssertion())
                append("audience", targetAudience)
                append("client_id", clientId)
            }
        )
        return exchangeResponse.accessToken
    }

    private fun createClientAssertion(): String {
        fun Instant.asDate() = Date(this.toEpochMilli())

        val now = Instant.now()

        return JWT.create()
            .withSubject(clientId)
            .withIssuer(clientId)
            .withAudience(tokenEndpoint)
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now.asDate())
            .withNotBefore(now.asDate())
            .withExpiresAt(now.plusSeconds(120).asDate())
            .withKeyId(privateJwk.keyID)
            .sign(signingAlgorithm)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExchangeResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("issued_token_type") val issuedTokenType: String,
    @JsonProperty("token_type") val tokenType: String,
    @JsonProperty("expires_in") val expiresIn: Int,
)