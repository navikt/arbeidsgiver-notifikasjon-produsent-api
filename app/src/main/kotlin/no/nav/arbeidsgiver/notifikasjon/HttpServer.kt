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
import no.nav.arbeidsgiver.notifikasjon.graphql.brukerGraphQL
import no.nav.arbeidsgiver.notifikasjon.graphql.produsentGraphQL
import org.slf4j.event.Level
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

@Suppress("MayBeConstant") /* wont work when using System.getenv */
object AuthConfig {
    val clientId = "produsent-api" /* System.getenv("AZURE_APP_CLIENT_ID") */
    val jwksUri = "https://fakedings.dev-gcp.nais.io/default/jwks" /* System.getenv("AZURE_OPENID_CONFIG_JWKS_URI") */
    val issuer = "https://fakedings.dev-gcp.nais.io/fake" /* System.getenv("AZURE_OPENID_CONFIG_ISSUER") */
}

typealias JWTAuthConfig = JWTAuthenticationProvider.Configuration.() -> Unit

private val defaultVerifierConfig: JWTAuthConfig = {
    verifier(
        JwkProviderBuilder(URL(AuthConfig.jwksUri))
            .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
            .rateLimited(10, 1, TimeUnit.MINUTES) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
            .build(),
        AuthConfig.issuer
    )
}

fun Application.module(
    verifierConfig: JWTAuthConfig = defaultVerifierConfig,
    produsentGraphql: GraphQL = produsentGraphQL(),
    brukerGraphql: GraphQL = brukerGraphQL()
) {
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
        jwt {
            verifierConfig()

            validate { credentials ->
                try {
                    requireNotNull(credentials.payload.audience) {
                        "Auth: Missing audience in token"
                    }
                    require(credentials.payload.audience.contains(AuthConfig.clientId)) {
                        "Auth: Valid audience not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }

    routing {
        route("internal") {
            get("alive") {
                call.respond(HttpStatusCode.OK)
            }
            get("ready") {
                call.respond(HttpStatusCode.OK)
            }

            get("metrics") {
                call.respond(meterRegistry.scrape())
            }
        }

        host("""produsent-api\..*""".toRegex()) {
            authenticate {
                route("api") {
                    post("graphql") {
                        val token = call.principal<JWTPrincipal>()!!.payload
                        val context = Context(
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

        host("""bruker-api\..*""".toRegex()) {
            route("api") {
                post("graphql") {
                    val request = call.receive<GraphQLRequest>()
                    val result = brukerGraphql.execute(request)
                    call.respond(result)
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
