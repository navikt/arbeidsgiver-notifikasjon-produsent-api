package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import io.ktor.server.auth.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.AzurePreAuthorizedApps
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.AzurePreAuthorizedAppsImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.*
import org.slf4j.MDC

data class BrukerPrincipal(
    val fnr: String,
) : Principal {
    companion object {
        fun validate(token: TokenIntrospectionResponse): BrukerPrincipal? = with(token) {
            val acrValid = other["acr"].let {
                it in listOf("idporten-loa-high", "Level4")
            }
            val pid = other["pid"]

            if (acrValid && pid is String) {
                return BrukerPrincipal(
                    fnr = pid,
                )
            }

            return null
        }
    }
}

data class ProdusentPrincipal(
    val appName: String,
) : Principal {
    init {
        MDC.put("preAuthorizedAppName", appName)
    }

    companion object {
        private val log = logger()

        fun validate(
            preAuthorizedApps: AzurePreAuthorizedApps,
            token: TokenIntrospectionResponse,
        ): ProdusentPrincipal? = with(token) {
            val azp = other["azp"] as? String ?: run {
                log.error("AzureAD missing azp-claim")
                return null
            }

            val appName = preAuthorizedApps.lookup(azp) ?: run {
                log.error("AzureAD, unknown azp=$azp")
                return null
            }

            return ProdusentPrincipal(
                appName = appName,
            )
        }
    }
}

object HttpAuthProviders {

    val BRUKER_API_AUTH by lazy {
        TexasAuthPluginConfiguration(
            client = AuthClientImpl(TexasAuthConfig.nais(), IdentityProvider.TOKEN_X),
            validate = { BrukerPrincipal.validate(it) }
        )
    }

    val PRODUSENT_API_AUTH by lazy {
        val preAuthorizedApps = AzurePreAuthorizedAppsImpl()

        TexasAuthPluginConfiguration(
            client = AuthClientImpl(TexasAuthConfig.nais(), IdentityProvider.AZURE_AD),
            validate = { ProdusentPrincipal.validate(preAuthorizedApps, it) }
        )
    }
}
