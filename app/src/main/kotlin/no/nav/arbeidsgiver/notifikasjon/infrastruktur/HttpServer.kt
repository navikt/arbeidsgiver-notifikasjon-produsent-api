package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.core.JsonProcessingException
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
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.BrukerContext
import no.nav.arbeidsgiver.notifikasjon.ProdusentContext
import no.nav.arbeidsgiver.notifikasjon.objectMapper
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

data class JwtAuthenticationParameters(
    val clientId: String,
    val jwksUri: String,
    val issuer: String
)

typealias JwtAuthentication = JWTAuthenticationProvider.Configuration.(JwtAuthenticationParameters) -> Unit
private val log = LoggerFactory.getLogger("HttpServer")!!
val STANDARD_JWT_AUTHENTICATION: JwtAuthentication = { parameter ->
    verifier(
        JwkProviderBuilder(URL(parameter.jwksUri))
            .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
            .rateLimited(
                10,
                1,
                TimeUnit.MINUTES
            ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
            .build(),
        parameter.issuer
    )

    validate { credentials ->
        try {
            log.debug(
                "validate credentials -> aud:{} iss:{} parameter:{}",
                credentials.payload.audience, credentials.payload.issuer, parameter
            )
            requireNotNull(credentials.payload.audience) {
                "Auth: Missing audience in token"
            }
            require(credentials.payload.audience.contains(parameter.clientId)) {
                "Auth: Valid audience not found in claims"
            }
            JWTPrincipal(credentials.payload)
        } catch (e: Throwable) {
            log.info("invalid credentials: {}", e.message)
            null
        }
    }
}

fun Application.httpServerSetup(
    jwtAuthentication: JwtAuthentication = STANDARD_JWT_AUTHENTICATION,
    produsentGraphQL: TypedGraphQL<ProdusentContext>,
    brukerGraphQL: TypedGraphQL<BrukerContext>,
) {

    install(CORS) {
        allowNonSimpleContentTypes = true
        host("arbeidsgiver-q.nav.no", schemes = listOf("https"))
        host("min-side-arbeidsgiver.dev.nav.no", schemes = listOf("https"))
    }

    install(MicrometerMetrics) {
        registry = Health.meterRegistry
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .build()
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            LogbackMetrics()
        )
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
        level = Level.INFO
        mdc("method") { call ->
            call.request.httpMethod.value
        }
        mdc("host") { call ->
            call.request.header("host")
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
        jwt(name = "produsent") {
            jwtAuthentication(JwtAuthenticationParameters(
                clientId = "produsent-api", /* System.getenv("AZURE_APP_CLIENT_ID") */
                jwksUri = "https://fakedings.dev-gcp.nais.io/default/jwks", /* System.getenv("AZURE_OPENID_CONFIG_JWKS_URI") */
                issuer = "https://fakedings.dev-gcp.nais.io/fake" /* System.getenv("AZURE_OPENID_CONFIG_ISSUER") */
            ))
        }
        jwt(name = "bruker") {
            jwtAuthentication(JwtAuthenticationParameters(
                clientId = "0090b6e1-ffcc-4c37-bc21-049f7d1f0fe5", /* System.getenv("AZURE_APP_CLIENT_ID") */
                jwksUri = "https://navtestb2c.b2clogin.com/navtestb2c.onmicrosoft.com/discovery/v2.0/keys?p=b2c_1a_idporten_ver1", /* System.getenv("AZURE_OPENID_CONFIG_JWKS_URI") */
                issuer = "https://navtestb2c.b2clogin.com/d38f25aa-eab8-4c50-9f28-ebf92c1256f2/v2.0/" /* System.getenv("AZURE_OPENID_CONFIG_ISSUER") */
            ))
        }
    }

    routing {
        route("internal") {
            internal()
        }

        host("""ag-notifikasjon-produsent-api\..*""".toRegex()) {
            authenticate("produsent") {
                route("api") {
                    produsentGraphQL("graphql", produsentGraphQL)
                    ide()
                }
            }
        }

        host("""ag-notifikasjon-bruker-api\..*""".toRegex()) {
            route("api") {
                authenticate("bruker") {
                    brukerGraphQL("graphql", brukerGraphQL)
                }
                ide()
            }
        }
    }
}

private val internalDispatcher: CoroutineContext = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
fun Route.internal() {
    get("alive") {
        withContext(this.coroutineContext + internalDispatcher) {
            if (Health.alive) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, Health.subsystemAlive)
            }
        }
    }

    get("ready") {
        withContext(this.coroutineContext + internalDispatcher) {
            if (Health.ready) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, Health.subsystemReady)
            }
        }
    }

    get("metrics") {
        withContext(this.coroutineContext + internalDispatcher) {
            call.respond(Health.meterRegistry.scrape())
        }
    }
}

fun Route.ide() {
    get("ide") {
        call.respondBytes(graphiqlHTML, ContentType.Text.Html)
    }
}

private val brukerGraphQLDispatcher: CoroutineContext = Executors.newFixedThreadPool(16).asCoroutineDispatcher()
fun Route.brukerGraphQL(
    path: String,
    graphQL: TypedGraphQL<BrukerContext>
) {
    post(path) {
        withContext(this.coroutineContext + brukerGraphQLDispatcher) {
            val token = call.principal<JWTPrincipal>()!!
            val authHeader = call.request.authorization()!!.removePrefix("Bearer ") //TODO skal veksles inn hos tokenX når altinnproxy kan validere et slikt token
            val request = call.receive<GraphQLRequest>()
            val result = graphQL.execute(request, BrukerContext(token.payload.subject, authHeader))
            call.respond(result)
        }
    }
}
private val produsentGraphQLDispatcher: CoroutineContext = Executors.newFixedThreadPool(16).asCoroutineDispatcher()
fun Route.produsentGraphQL(
    path: String,
    graphQL: TypedGraphQL<ProdusentContext>
) {
    post(path) {
        withContext(this.coroutineContext + produsentGraphQLDispatcher) {
            val context = ProdusentContext(payload = call.principal<JWTPrincipal>()!!.payload)
            val request = call.receive<GraphQLRequest>()
            val result = graphQL.execute(request, context)
            call.respond(result)
        }
    }
}

private val graphiqlHTML: ByteArray =
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
        .trimIndent()
        .toByteArray()
