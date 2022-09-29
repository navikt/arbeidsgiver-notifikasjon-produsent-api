package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Verification
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.AzurePreAuthorizedAppsImpl
import java.net.URL
import java.util.concurrent.TimeUnit

data class BrukerPrincipal(
    val fnr: String,
) : Principal

data class ProdusentPrincipal(
    val appName: String,
) : Principal

data class JWTAuthentication(
    val name: String,
    val config:  JWTAuthenticationProvider.Config.() -> Unit,
)

object HttpAuthProviders {
    private val log = logger()

    private val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson()
        }
        install(PropagateFromMDCPlugin) {
            propagate("x_correlation_id")
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
        }
    }

    val TOKEN_X by lazy {
        JWTAuthentication(
            name = "tokenx",
            config = {
                verifier(
                    audience = System.getenv("TOKEN_X_CLIENT_ID"),
                    discoveryUrl = System.getenv("TOKEN_X_WELL_KNOWN_URL")
                ) {
                    `with id-porten login level 4`()
                }

                validate {
                    BrukerPrincipal(
                        fnr = it.payload.getClaim("pid").asString()
                    )
                }
            }
        )
    }

    val LOGIN_SERVICE by lazy {
        JWTAuthentication(
            name = "loginservice",
            config = {
                verifier(
                    audience = System.getenv("LOGINSERVICE_IDPORTEN_AUDIENCE"),
                    discoveryUrl = System.getenv("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
                ) {
                    `with id-porten login level 4`()
                }

                validate {
                    BrukerPrincipal(fnr = it.payload.getClaim("pid").asString())
                }
            }
        )
    }

    val AZURE_AD by lazy {
        val preAuthorizedApps = AzurePreAuthorizedAppsImpl()

        JWTAuthentication(
            name = "azuread",
            config = {
                verifier(
                    issuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER"),
                    jwksUri = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI"),
                    audience = System.getenv("AZURE_APP_CLIENT_ID"),
                )

                validate {
                    val azp = it.payload.getClaim("azp").asString() ?: run {
                        log.error("AzureAD missing azp-claim")
                        return@validate null
                    }

                    val appName = preAuthorizedApps.lookup(azp) ?: run {
                        log.error("AzureAD, unknown azp=$azp")
                        return@validate null
                    }

                    ProdusentPrincipal(appName = appName)
                }
            }
        )
    }

    val FAKEDINGS_BRUKER by UnavailableInProduction {
        JWTAuthentication(
            name = "fakedings",
            config = {
                verifier(
                    audience = "bruker-api",
                    discoveryUrl = "https://fakedings.dev-gcp.nais.io/fake/.well-known/openid-configuration"
                ) {
                    `with id-porten login level 4`()
                }

                validate {
                    BrukerPrincipal(
                        fnr = it.payload.subject
                    )
                }
            }
        )
    }

    val FAKEDINGS_PRODUSENT by UnavailableInProduction {
        JWTAuthentication(
            name = "fakedings",
            config = {
                verifier(
                    audience = "produsent-api",
                    discoveryUrl = "https://fakedings.dev-gcp.nais.io/fake/.well-known/openid-configuration",
                )

                validate {
                    ProdusentPrincipal(
                        appName = it.payload.getClaim("azp").asString()
                    )
                }
            }
        )
    }

    fun Verification.`with id-porten login level 4`() {
        withClaim("acr", "Level4")
    }

    private fun JWTAuthenticationProvider.Config.verifier(
        audience: String,
        discoveryUrl: String,
        additionalVerification: Verification.() -> Unit = {},
    ) {
        val metaData = runBlocking {
            httpClient.get(discoveryUrl).body<AuthorizationServerMetaData>()
        }
        verifier(
            issuer = metaData.issuer,
            jwksUri = metaData.jwks_uri,
            audience = audience,
            additionalVerification = additionalVerification,
        )
    }

    private fun JWTAuthenticationProvider.Config.verifier(
        issuer: String,
        jwksUri: String,
        audience: String,
        additionalVerification: Verification.() -> Unit = {},
    ) {
        log.info("configuring authentication $name with issuer: $issuer, jwksUri: $jwksUri, audience: $audience")

        val jwkProvider = JwkProviderBuilder(URL(jwksUri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(
                10,
                1,
                TimeUnit.MINUTES
            )
            .build()

        verifier(jwkProvider, issuer) {
            withAudience(audience)
            additionalVerification()
        }
    }

    /* Fields from https://datatracker.ietf.org/doc/html/rfc8414 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AuthorizationServerMetaData(
        val issuer: String,
        val jwks_uri: String,
        val id_token_signing_alg_values_supported: List<String>? = null,
    )
}



fun AuthenticationConfig.configureProviders(providers: Iterable<JWTAuthentication>) {
    for ((name, config) in providers) {
        jwt(name = name) {
            config()
        }
    }
}

fun Route.authenticate(providers: Iterable<JWTAuthentication>, body: Route.() -> Unit) {
    val names = providers.map { it.name }
    authenticate(*names.toTypedArray()) {
        body()
    }
}
