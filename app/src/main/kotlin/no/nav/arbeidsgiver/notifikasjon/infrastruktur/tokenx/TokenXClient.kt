package no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.benmanes.caffeine.cache.Expiry
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.serialization.jackson.jackson
import io.micrometer.core.instrument.Counter
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.HttpClientMetricsFeature
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.cache.caffeineBuilder
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

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

    private val jwsSigner: JWSSigner = RSASSASigner(config.privateKey)
    private val jwsHeader: JWSHeader = JWSHeader.Builder(JWSAlgorithm.RS256)
        .keyID(config.privateKey.keyID)
        .type(JOSEObjectType.JWT)
        .build()

    private data class CacheKey(
        private val audience: String,
        private val subjectToken: String,
    )

    @OptIn(ExperimentalTime::class)
    private val exchangeCache = caffeineBuilder<CacheKey, AccessToken> {
        maximumSize = 5_000L

        expireAfter = (object : Expiry<CacheKey, AccessToken> {
            override fun expireAfterCreate(key: CacheKey, response: AccessToken, currentTime: Long) =
                (response.expires_in.seconds - 5.seconds).inWholeNanoseconds

            override fun expireAfterUpdate(
                key: CacheKey,
                value: AccessToken,
                currentTime: Long,
                currentDuration: Long
            ) = currentDuration

            override fun expireAfterRead(
                key: CacheKey,
                value: AccessToken,
                currentTime: Long,
                currentDuration: Long
            ) = currentDuration
        })
    }
        .build()

    private val log = logger()
    private val successCounter = Counter.builder("tokenx.success")
        .register(Metrics.meterRegistry)
    private val failCounter = Counter.builder("tokenx.fail")
        .register(Metrics.meterRegistry)

    override suspend fun exchange(subjectToken: String, audience: String): String {
        val accessTokenEntry = exchangeCache.get(CacheKey(audience, subjectToken)) {
            try {
                val assertion = makeClientAssertion()

                httpClient.post(config.tokenEndpoint) {
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
                    .body<AccessToken>()
                    .also {
                        successCounter.increment()
                    }
            } catch (e: Exception) {
                log.info("tokene exchange failed (audience='{}').", audience, e)
                failCounter.increment()
                throw e
            }
        }
        return accessTokenEntry.access_token
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
    val expires_in: Long,
)

data class TokenXConfig(
    val clientId: String = System.getenv("TOKEN_X_CLIENT_ID"),
    val privateKey: RSAKey = RSAKey.parse(System.getenv("TOKEN_X_PRIVATE_JWK")),
    val tokenEndpoint: String = System.getenv("TOKEN_X_TOKEN_ENDPOINT"),
)