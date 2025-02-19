package no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx

import io.micrometer.core.instrument.Counter
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.IdentityProvider
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthConfig


interface TokenXClient {
    suspend fun exchange(subjectToken: String, audience: String): String
}

class TokenXClientImpl : TokenXClient {

    private val authClient = AuthClientImpl(TexasAuthConfig.nais(), IdentityProvider.TOKEN_X)

    private val log = logger()
    private val successCounter = Counter.builder("tokenx.success")
        .register(Metrics.meterRegistry)
    private val failCounter = Counter.builder("tokenx.fail")
        .register(Metrics.meterRegistry)

    override suspend fun exchange(subjectToken: String, audience: String) =
        authClient.exchange(audience, subjectToken).fold(
            onSuccess = { token ->
                successCounter.increment()
                token.accessToken
            },
            onError = { r ->
                log.info("token exchange failed (audience='{}'). {}", audience, r.error)
                failCounter.increment()
                throw RuntimeException("Failed to exchange token: ${r.error}")
            }
        )
}