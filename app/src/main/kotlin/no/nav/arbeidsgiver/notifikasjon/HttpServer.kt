package no.nav.arbeidsgiver.notifikasjon

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.core.JsonProcessingException
import graphql.GraphQL
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.metrics.micrometer.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.arbeidsgiver.notifikasjon.graphql.BrukerContext
import no.nav.arbeidsgiver.notifikasjon.graphql.ProdusentContext
import no.nav.arbeidsgiver.notifikasjon.graphql.brukerGraphQL
import no.nav.arbeidsgiver.notifikasjon.graphql.produsentGraphQL
import org.slf4j.event.Level
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

data class AuthConfig(val clientId: String, val jwksUri: String, val issuer: String)

typealias JWTAuthConfig = JWTAuthenticationProvider.Configuration.(AuthConfig) -> Unit

fun JWTAuthenticationProvider.Configuration.standardAuthenticationConfiguration(authConfig: AuthConfig) {
    verifier(
        JwkProviderBuilder(URL(authConfig.jwksUri))
            .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
            .rateLimited(
                10,
                1,
                TimeUnit.MINUTES
            ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
            .build(),
        authConfig.issuer
    )

    validate { credentials ->
        try {
            requireNotNull(credentials.payload.audience) {
                "Auth: Missing audience in token"
            }
            require(credentials.payload.audience.contains(authConfig.clientId)) {
                "Auth: Valid audience not found in claims"
            }
            JWTPrincipal(credentials.payload)
        } catch (e: Throwable) {
            null
        }
    }
}

fun Application.module(
    authenticationConfiguration: JWTAuthConfig = JWTAuthenticationProvider.Configuration::standardAuthenticationConfiguration,
    produsentGraphql: GraphQL = produsentGraphQL(),
    brukerGraphql: GraphQL = brukerGraphQL()
) {

    install(CORS) {
        allowNonSimpleContentTypes = true
        host("arbeidsgiver-q.nav.no", schemes = listOf("https"))
    }

    install(MicrometerMetrics) {
        registry = meterRegistry
    }

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        retrieveFromHeader(HttpHeaders.XCorrelationId)
        retrieveFromHeader("call-id")
        retrieveFromHeader("callId")
        retrieveFromHeader("call_id")

        generate {
            UUID.randomUUID().toString()
        }

        replyToHeader(HttpHeaders.XCorrelationId)
    }

    install(CallLogging) {
        filter { call ->
            !call.request.path().startsWith("/internal/")
        }

        level = Level.INFO
        mdc("method") { call ->
            call.request.httpMethod.value
        }
        mdc("path") { call ->
            call.request.path()
        }
        callIdMdc("x_correlation_id")
    }

    install(StatusPages) {
        exception<JsonProcessingException> { ex ->
            ex.clearLocation()

            log.warn("unhandled exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
            call.respond(
                HttpStatusCode.InternalServerError, mapOf(
                    "error" to "unexpected error",
                )
            )
        }

        exception<RuntimeException> { ex ->
            log.warn("unhandled exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
            call.respond(
                HttpStatusCode.InternalServerError, mapOf(
                    "error" to "unexpected error",
                )
            )
        }

        exception<Throwable> { ex ->
            log.warn("unhandled exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
            throw ex
        }
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }

    install(Authentication) {
        jwt(name = "Produsent") {
            authenticationConfiguration(
                AuthConfig(
                    clientId = "produsent-api", /* System.getenv("AZURE_APP_CLIENT_ID") */
                    jwksUri = "https://fakedings.dev-gcp.nais.io/default/jwks", /* System.getenv("AZURE_OPENID_CONFIG_JWKS_URI") */
                    issuer = "https://fakedings.dev-gcp.nais.io/fake" /* System.getenv("AZURE_OPENID_CONFIG_ISSUER") */
                )
            )
        }
        jwt(name = "Bruker") {
            authenticationConfiguration(
                AuthConfig(
                    clientId = "0090b6e1-ffcc-4c37-bc21-049f7d1f0fe5", /* System.getenv("AZURE_APP_CLIENT_ID") */
                    jwksUri = "https://navtestb2c.b2clogin.com/navtestb2c.onmicrosoft.com/discovery/v2.0/keys?p=b2c_1a_idporten_ver1", /* System.getenv("AZURE_OPENID_CONFIG_JWKS_URI") */
                    issuer = "https://navtestb2c.b2clogin.com/d38f25aa-eab8-4c50-9f28-ebf92c1256f2/v2.0/" /* System.getenv("AZURE_OPENID_CONFIG_ISSUER") */
                )
            )
        }
    }

    routing {
        route("internal") {
            get("alive") {
                if (livenessGauge.all { it.value } ) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.ServiceUnavailable, livenessGauge)
                }
            }

            get("ready") {
                if (readinessGauge.all { it.value } ) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.ServiceUnavailable, livenessGauge)
                }
            }

            get("metrics") {
                call.respond(meterRegistry.scrape())
            }
        }

        host("""ag-notifikasjon-produsent-api\..*""".toRegex()) {
            authenticate("Produsent") {
                route("api") {
                    post("graphql") {
                        val token = call.principal<JWTPrincipal>()!!.payload
                        val context = ProdusentContext(
                            produsentId = "iss:${token.issuer} sub:${token.subject}"
                        )
                        val request = call.receive<GraphQLRequest>()
                        val result = produsentGraphql.execute(request, context)
                        call.respond(result)
                    }
                    get("ide") {
                        call.respondBytes(graphiqlHTML.trimIndent().toByteArray(), ContentType.parse("text/html"))
                    }
                }
            }
        }

        host("""ag-notifikasjon-bruker-api\..*""".toRegex()) {
            route("api") {
                authenticate("Bruker") {
                    post("graphql") {
                        val token = call.principal<JWTPrincipal>()!!.payload

                        val request = call.receive<GraphQLRequest>()
                        val result = brukerGraphql.execute(request, BrukerContext(token.subject))
                        call.respond(result)
                    }
                }
                get("ide") {
                    call.respondBytes(
                        graphiqlHTML.trimIndent().toByteArray(),
                        ContentType.parse("text/html")
                    )
                }

            }
        }
    }
}

private const val graphiqlHTML =
    """
    <html>
      <head>
        <title>GraphQL explorer</title>
        <link href="https://unpkg.com/graphiql/graphiql.min.css" rel="stylesheet" />
      </head>
      <body style="margin: 0;">
        <div id="graphiql" style="height: 100vh;"></div>

        <script
          crossorigin
          src="https://unpkg.com/react/umd/react.production.min.js"
        ></script>
        <script
          crossorigin
          src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"
        ></script>
        <script
          crossorigin
          src="https://unpkg.com/graphiql/graphiql.min.js"
        ></script>

        <script>
          const fetcher = GraphiQL.createFetcher({
            url: '/api/graphql', 
            enableIncrementalDelivery: false
          });

          ReactDOM.render(
            React.createElement(GraphiQL, { fetcher: fetcher }),
            document.getElementById('graphiql'),
          );
        </script>
      </body>
    </html>
    """
