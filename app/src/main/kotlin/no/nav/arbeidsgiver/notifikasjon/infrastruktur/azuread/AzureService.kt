package no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.IdentityProvider
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthConfig

interface AzureService {
    suspend fun getAccessToken(targetApp: String): String
}

object AzureServiceImpl : AzureService {

    private val authClient = AuthClientImpl(TexasAuthConfig.nais(), IdentityProvider.AZURE_AD)

    override suspend fun getAccessToken(targetApp: String): String =
        authClient.token(targetApp).fold(
            onSuccess = { it.accessToken },
            onError = { throw Exception("Failed to fetch token: ${it.error}") }
        )

}
