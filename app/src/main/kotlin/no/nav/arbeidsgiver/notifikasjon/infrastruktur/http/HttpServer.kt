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
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.TypedGraphQL
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.WithCoroutineScope
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produceMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.produsent.api.ProdusentAPI
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

fun extractProdusentContext(produsentRegister: ProdusentRegister) =
    fun PipelineContext<Unit, ApplicationCall>.(): ProdusentAPI.Context {
        val principal = call.principal<ProdusentPrincipal>()!!
        return ProdusentAPI.Context(
            appName = principal.appName,
            produsent = produsentRegister.finn(principal.appName),
            coroutineScope = this
        )
    }

private val metricsDispatcher: CoroutineContext = Executors.newFixedThreadPool(1)
    .produceMetrics("internal-http")
    .asCoroutineDispatcher()

private val graphQLDispatcher: CoroutineContext = Executors.newFixedThreadPool(16)
    .produceMetrics("graphql-workers")
    .asCoroutineDispatcher()

fun CoroutineScope.launchHttpServer(
    httpPort: Int,
    customRoute: Routing.() -> Unit = {},
    application: Application.() -> Unit = { baseSetup(listOf(), customRoute) }
) {
    launch {
        embeddedServer(Netty, port = httpPort, configure = {
            connectionGroupSize = 16
            callGroupSize = 16
            workerGroupSize = 16
        }) {
            application()
        }
            .start(wait = true)
    }
}

fun <T : WithCoroutineScope> CoroutineScope.launchGraphqlServer(
    httpPort: Int,
    authProviders: List<JWTAuthentication> = listOf(),
    extractContext: PipelineContext<Unit, ApplicationCall>.() -> T,
    graphql: Deferred<TypedGraphQL<T>>,
) {
    launchHttpServer(httpPort) {
        graphqlSetup(authProviders, extractContext, graphql)
    }
}

fun <T : WithCoroutineScope> Application.graphqlSetup(
    authProviders: List<JWTAuthentication>,
    extractContext: PipelineContext<Unit, ApplicationCall>.() -> T,
    graphql: Deferred<TypedGraphQL<T>>,
) {
    baseSetup(authProviders = authProviders) {
        authenticate(authProviders) {
            route("api") {
                post("graphql") {
                    withContext(this.coroutineContext + graphQLDispatcher) {
                        val context = extractContext()
                        val request = call.receive<GraphQLRequest>()
                        val result = graphql.await().execute(request, context)
                        call.respond(result)
                    }
                }
            }
        }
    }
}

fun Application.baseSetup(
    authProviders: List<JWTAuthentication>,
    customRoute: Routing.() -> Unit,
) {
    install(CORS) {
        /* TODO: log when reject */
        allowNonSimpleContentTypes = true
        when (System.getenv("NAIS_CLUSTER_NAME")) {
            "prod-gcp" -> {
                host("*.nav.no", schemes = listOf("https"))
            }
            "dev-gcp" -> {
                host("*.nav.no", schemes = listOf("https"))
                host("localhost:3000")
            }
        }
    }

    install(MicrometerMetrics) {
        registry = Metrics.meterRegistry
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
        disableDefaultColors()
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
        register(ContentType.Application.Json, TimedContentConverter(JacksonConverter(laxObjectMapper)))
    }

    install(Authentication) {
        configureProviders(authProviders)
    }

    routing {
        trace { application.log.trace(it.buildText()) }

        customRoute()

        route("internal") {
            get("alive") {
                if (Health.alive) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        Health.subsystemAlive.toString()
                    )
                }
            }

            get("ready") {
                if (Health.ready) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        Health.subsystemReady.toString()
                    )
                }
            }

            get("metrics") {
                withContext<Unit>(coroutineContext + metricsDispatcher) {
                    call.respond<String>(Metrics.meterRegistry.scrape())
                }
            }
        }
    }
}
