package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Verification
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.routing.*
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.util.concurrent.TimeUnit

data class BrukerPrincipal(
    val fnr: String,
) : Principal

data class ProdusentPrincipal(
    val subject: String,
) : Principal

data class JWTAuthentication(
    val name: String,
    val config:  JWTAuthenticationProvider.Configuration.() -> Unit,
)

object AuthConfigs {
    val log = logger()

    val httpClient = HttpClient(Apache) {
       install(JsonFeature) {
           serializer = JacksonSerializer()
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
                    val fnrClaim = when (it.payload.getClaim("idp").asString()) {
                        "loginservice" ->  "sub"
                        "idporten" -> "idn"
                        null -> throw Error()
                        else -> throw Error()
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
        JWTAuthentication(
            name = "azuread",
            config = {
                verifier(
                    issuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER"),
                    jwksUri = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI"),
                    audience = System.getenv("AZURE_APP_CLIENT_ID"),
                )

                validate {
                    ProdusentPrincipal(subject = it.payload.subject)
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
                        subject = it.payload.subject
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
        AuthConfigs.httpClient.get<AuthorizationServerMetaData>(discoveryUrl)
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
    AuthConfigs.log.info("configuring authentication $name with issuer: $issuer, jwksUri: $jwksUri, audience: $audience")

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

fun Authentication.Configuration.configureProviders(prefix: String, providers: Iterable<JWTAuthentication>) {
    for ((name, config) in providers) {
        jwt(name = "$prefix-${name}") {
            config()
        }
    }
}

fun Route.authenticate(prefix: String, providers: Iterable<JWTAuthentication>, body: Route.() -> Unit) {
    val names = providers.map { "$prefix-${it.name}" }
    authenticate(*names.toTypedArray()) {
        body()
    }
}
