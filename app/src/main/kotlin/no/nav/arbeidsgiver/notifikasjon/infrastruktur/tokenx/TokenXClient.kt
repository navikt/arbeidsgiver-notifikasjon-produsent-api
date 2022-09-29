package no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.HttpClientMetricsFeature
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import java.time.Instant
import java.util.*

fun defaultHttpClient() = HttpClient(Apache) {
    install(ContentNegotiation) {
        jackson()
    }
    install(HttpClientMetricsFeature) {
        registry = Metrics.meterRegistry
    }
}


interface TokenXClient {
    suspend fun exchange(subjectToken: String, audience: String): String
}

/**
 * lånt med modifikasjon fra https://github.com/navikt/tokendings-latency-tester/tree/master/src/main/kotlin/no/nav
 */
class TokenXClientImpl(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val config: TokenXConfig = TokenXConfig(),
) : TokenXClient {
    private val jwsSigner: JWSSigner
    private val algorithm: JWSAlgorithm = JWSAlgorithm.RS256
    private val jwsHeader: JWSHeader

    init {
        val privateKey = config.privateKey
        jwsSigner = RSASSASigner(privateKey)
        jwsHeader = JWSHeader.Builder(algorithm)
            .keyID(privateKey.keyID)
            .type(JOSEObjectType.JWT)
            .build()
    }

    override suspend fun exchange(subjectToken: String, audience: String): String {
        val assertion = makeClientAssertion()

        val accessTokenResponse = httpClient.post(config.tokenEndpoint) {
            accept(ContentType.Application.Json)
            setBody(FormDataContent(
                Parameters.build {
                    append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                    append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                    append("client_assertion", assertion)
                    append("subject_token_type", "urn:ietf:params:oauth:token-type:jwt")
                    append("subject_token", subjectToken)
                    append("audience", audience)
                }
            ))
        }
        return accessTokenResponse.body<AccessToken>().access_token
    }

    private fun makeClientAssertion(): String {
        val now = Date.from(Instant.now())
        val later = Date.from(Instant.now().plusSeconds(60))
        return JWTClaimsSet.Builder()
            .audience(config.tokenEndpoint)
            .subject(config.clientId)
            .issuer(config.clientId)
            .jwtID(UUID.randomUUID().toString())
            .notBeforeTime(now)
            .issueTime(now)
            .expirationTime(later)
            .build()
            .let { claims -> SignedJWT(jwsHeader, claims) }
            .apply { sign(jwsSigner) }
            .serialize()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessToken(
    val access_token: String,
    val expires_in: Int
)

data class TokenXConfig(
    val clientId: String = System.getenv("TOKEN_X_CLIENT_ID"),
    val privateKey: RSAKey = RSAKey.parse(System.getenv("TOKEN_X_PRIVATE_JWK")),
    val tokenEndpoint: String = System.getenv("TOKEN_X_TOKEN_ENDPOINT"),
)