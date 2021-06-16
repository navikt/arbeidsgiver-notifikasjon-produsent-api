package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.metrics.micrometer.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentAPI
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.slf4j.event.Level
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

val extractBrukerContext = fun PipelineContext<Unit, ApplicationCall>.(): BrukerAPI.Context {
    val principal = call.principal<BrukerPrincipal>()!!
    val authHeader = call.request.authorization()!!.removePrefix("Bearer ")
    return BrukerAPI.Context(
        fnr = principal.fnr,
        authHeader,
        coroutineScope = this
    )
}

val extractProdusentContext = fun PipelineContext<Unit, ApplicationCall>.(): ProdusentAPI.Context {
    val principal = call.principal<ProdusentPrincipal>()!!
    return ProdusentAPI.Context(
        produsentid = principal.subject,
        coroutineScope = this
    )
}

private val metricsDispatcher: CoroutineContext = Executors.newFixedThreadPool(1)
    .produceMetrics("internal-http")
    .asCoroutineDispatcher()

private val graphQLDispatcher: CoroutineContext = Executors.newFixedThreadPool(16)
    .produceMetrics("graphql-workers")
    .asCoroutineDispatcher()

fun <T : WithCoroutineScope> Application.httpServerSetup(
    authProviders: List<JWTAuthentication>,
    extractContext: PipelineContext<Unit, ApplicationCall>.() -> T,
    graphql: TypedGraphQL<T>,
) {

    install(CORS) {
        /* TODO: log when reject */
        allowNonSimpleContentTypes = true
        host("min-side-arbeidsgiver.dev.nav.no", schemes = listOf("https"))
        host("localhost:3000")
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

        filter { call ->
            !call.request.path().startsWith("/internal/")
        }

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
        configureProviders(authProviders)
    }

    routing {
        trace { application.log.trace(it.buildText()) }

        authenticate(authProviders) {
            route("api") {
                post("graphql") {
                    withContext(this.coroutineContext + graphQLDispatcher) {
                        val context = extractContext()
                        val request = call.receive<GraphQLRequest>()
                        val result = graphql.execute(request, context)
                        call.respond(result)
                    }
                }
            }
        }

        get("ide") {
            call.respondBytes(graphiqlHTML, ContentType.Text.Html)
        }

        route("internal") {
            get("alive") {
                if (Health.alive) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.ServiceUnavailable, Health.subsystemAlive)
                }
            }

            get("ready") {
                if (Health.ready) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.ServiceUnavailable, Health.subsystemReady)
                }
            }

            get("metrics") {
                withContext(this.coroutineContext + metricsDispatcher) {
                    call.respond(Health.meterRegistry.scrape())
                }
            }
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
