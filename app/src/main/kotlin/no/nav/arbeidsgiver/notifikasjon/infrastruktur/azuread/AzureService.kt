package no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

interface AzureService {
    suspend fun getAccessToken(targetApp: String): String
}

object AzureServiceImpl : AzureService {
    private const val maxCachedEntries = 1000L
    private const val cacheExpiryMarginSeconds = 5

    private val cache: Cache<String, AccessTokenEntry> = Caffeine.newBuilder()
        .expireAfter(object : Expiry<String, AccessTokenEntry> {
            override fun expireAfterCreate(key: String, response: AccessTokenEntry, currentTime: Long): Long {
                return TimeUnit.SECONDS.toNanos(response.expiresInSeconds - cacheExpiryMarginSeconds)
            }
            override fun expireAfterUpdate(key: String, value: AccessTokenEntry, currentTime: Long, currentDuration: Long): Long = currentDuration
            override fun expireAfterRead(key: String, value: AccessTokenEntry, currentTime: Long, currentDuration: Long): Long = currentDuration
        })
        .maximumSize(maxCachedEntries)
        .build()

    override suspend fun getAccessToken(targetApp: String): String {
        return cache.get(targetApp) {
            runBlocking {
                performTokenExchange(targetApp)
            }
        }.accessToken
    }

    private suspend fun performTokenExchange(targetApp: String): AccessTokenEntry {
        return AzureClient.fetchToken(
            targetApp,
            ClientAssertionService.createClientAssertion(),
        ).let {
            AccessTokenEntry(
                accessToken = it.accessToken,
                expiresInSeconds = it.expiresIn.toLong()
            )
        }
    }

}

internal data class AccessTokenEntry(
    val accessToken: String,
    val expiresInSeconds: Long
)

internal object ClientAssertionService {
    private val privateRsaKey by lazy {
        RSAKey.parse(AzureEnv.jwk)
    }

    fun createClientAssertion(): String {
        val now = Date.from(Instant.now())
        return JWTClaimsSet.Builder()
            .issuer(AzureEnv.clientId)
            .subject(AzureEnv.clientId)
            .audience(AzureEnv.openidIssuer)
            .issueTime(now)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .jwtID(UUID.randomUUID().toString())
            .build()
            .sign(privateRsaKey)
            .serialize()
    }

    private fun JWTClaimsSet.sign(rsaKey: RSAKey): SignedJWT =
        SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.keyID)
                .type(JOSEObjectType.JWT).build(),
            this
        ).apply {
            sign(RSASSASigner(rsaKey.toPrivateKey()))
        }
}

