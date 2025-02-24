package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.cache.getAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.defaultHttpClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.IdentityProvider
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthConfig
import java.time.Duration
import java.time.Instant.now


/**
 * klient for å utveksle en maskinporten token til en altinn plattform token,
 * slik det er beskrevet i dokumentasjonen til Altinn:
 * https://docs.altinn.studio//nb/api/scenarios/authentication/#exchange-of-jwt-token
 *
 * /authentication/api/v1/exchange/maskinporten
 */
class AltinnPlattformTokenClient(
   private val altinnBaseUrl: String = System.getenv("ALTINN_3_API_BASE_URL"),
   private val authClient: AuthClient = AuthClientImpl(TexasAuthConfig.nais(), IdentityProvider.MASKINPORTEN),
   private val httpClient: HttpClient = defaultHttpClient()
) {


    private val expiryBuffer = Duration.ofSeconds(60)
    private val plattformTokenCache = Caffeine.newBuilder()
        .recordStats()
        .expireAfter(Expiry.creating<String, DecodedJWT> { _, v ->
            v.expiresAtAsInstant.minus(expiryBuffer).let { expiry ->
                if (now().isAfter(expiry)) {
                    // handles edge case if token minus buffer is in the past
                    Duration.ZERO
                } else {
                    Duration.between(now(), expiry)
                }
            }
        })
        .buildAsync<String, DecodedJWT>().also {
            CaffeineCacheMetrics(
                it.synchronous(),
                "altinnPlattformToken",
                emptyList()
            ).bindTo(Metrics.meterRegistry)
        }

    suspend fun token(scope: String): String = plattformTokenCache.getAsync(scope) {
        val maskinportenToken = authClient.token(scope).fold(
            onSuccess = { it.accessToken },
            onError = { throw RuntimeException("Failed to get token: $it") }
        )

        val plattformToken: DecodedJWT = httpClient.get {
            url("$altinnBaseUrl/authentication/api/v1/exchange/maskinporten")
            header("Authorization", "Bearer $maskinportenToken")
        }.body<String>().let {
            JWT.decode(it)
        }

        plattformToken
    }.token
}