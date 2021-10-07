package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Verification
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AzurePreAuthorizedAppsImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.UnavailableInProduction
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.slf4j.MDC
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
    val config:  JWTAuthenticationProvider.Configuration.() -> Unit,
)

object HttpAuthProviders {
    val log = logger()

    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        defaultRequest {
            MDC.get("x_correlation_id")?.let {
                header("x_correlation_id", it)
            }
        }
    }

    private val idportenIssuer = Regex(
        option = RegexOption.COMMENTS,
        pattern = """
                ^ https://oidc .* difi .* \.no/idporten-oidc-provider/ $
            """)

    private val loginserviceIssuer = Regex(
        option = RegexOption.COMMENTS,
        pattern = """
                ^ https://nav (no | test) b2c\.b2clogin\.com/ .* $
             """
    )

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
                    val idp = it.payload.getClaim("idp").asString() ?: ""
                    val fnrClaim = when {
                        idp.matches(loginserviceIssuer) ->  "sub"
                        idp.matches(idportenIssuer) -> "pid"
                        else -> throw RuntimeException("Ukjent idp i TokenX-token")
                    }
                    BrukerPrincipal(
                        fnr = it.payload.getClaim(fnrClaim).asString()
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
                    BrukerPrincipal(fnr = it.payload.subject)
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
}

fun Verification.`with id-porten login level 4`() {
    withClaim("acr", "Level4")
}

fun JWTAuthenticationProvider.Configuration.verifier(
    audience: String,
    discoveryUrl: String,
    additionalVerification: Verification.() -> Unit = {},
) {
    val metaData = runBlocking {
        HttpAuthProviders.httpClient.get<AuthorizationServerMetaData>(discoveryUrl)
    }
    verifier(
        issuer = metaData.issuer,
        jwksUri = metaData.jwks_uri,
        audience = audience,
        additionalVerification = additionalVerification,
    )
}

fun JWTAuthenticationProvider.Configuration.verifier(
    issuer: String,
    jwksUri: String,
    audience: String,
    additionalVerification: Verification.() -> Unit = {},
) {
    HttpAuthProviders.log.info("configuring authentication $name with issuer: $issuer, jwksUri: $jwksUri, audience: $audience")

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

fun Authentication.Configuration.configureProviders(providers: Iterable<JWTAuthentication>) {
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
