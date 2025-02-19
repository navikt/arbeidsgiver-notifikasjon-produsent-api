package no.nav.arbeidsgiver.notifikasjon.infrastruktur.http

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.TypedGraphQL
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.WithCoroutineScope
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.timedExecute
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produceMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuth
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TexasAuthPluginConfiguration
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
    application: Application.() -> Unit = { baseSetup(customRoute) }
) {
    launch {
        embeddedServer(CIO, port = httpPort) {
            application()
        }
            .start(wait = true)
    }
}

fun <T : WithCoroutineScope> CoroutineScope.launchGraphqlServer(
    httpPort: Int,
    authPluginConfig: TexasAuthPluginConfiguration,
    extractContext: PipelineContext<Unit, ApplicationCall>.() -> T,
    graphql: Deferred<TypedGraphQL<T>>,
) {
    launchHttpServer(httpPort) {
        graphqlSetup(authPluginConfig, extractContext, graphql)
    }
}

fun <T : WithCoroutineScope> Application.graphqlSetup(
    authPluginConfig: TexasAuthPluginConfiguration,
    extractContext: PipelineContext<Unit, ApplicationCall>.() -> T,
    graphql: Deferred<TypedGraphQL<T>>,
) {
    baseSetup {
        route("api") {

            install(TexasAuth) {
                client = authPluginConfig.client
                validate = authPluginConfig.validate
            }

            post("graphql") {
                withContext(this.coroutineContext + graphQLDispatcher + MDCContext()) {
                    val context = extractContext()
                    val request = call.receive<GraphQLRequest>()
                    val result = graphql.await().timedExecute(request, context)
                    call.respond(result.toSpecification())
                }
            }
        }
    }
}

fun Application.baseSetup(
    customRoute: Routing.() -> Unit,
) {
    install(CORS) {
        /* TODO: log when reject */
        allowNonSimpleContentTypes = true
        when (System.getenv("NAIS_CLUSTER_NAME")) {
            "prod-gcp" -> {
                allowHost("*.nav.no", schemes = listOf("https"))
            }

            "dev-gcp" -> {
                allowHost("*.nav.no", schemes = listOf("https"))
                allowHost("localhost:3000")
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
        mdc("preAuthorizedAppName") { call ->
            call.principal<ProdusentPrincipal>()?.appName
        }
        callIdMdc("x_correlation_id")
    }

    install(StatusPages) {
        exception<BadRequestException> { call, ex ->
            this@baseSetup.log.warn("unhandled exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
            call.respond(
                HttpStatusCode.InternalServerError, mapOf(
                    "error" to "unexpected error",
                )
            )
        }

        exception<JsonProcessingException> { call, ex ->
            ex.clearLocation()

            this@baseSetup.log.error("unhandled exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
            call.respond(
                HttpStatusCode.InternalServerError, mapOf(
                    "error" to "unexpected error",
                )
            )
        }

        exception<Throwable> { call, ex ->
            this@baseSetup.log.error("unhandled exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
            call.respond(
                HttpStatusCode.InternalServerError, mapOf(
                    "error" to "unexpected error",
                )
            )
        }
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, TimedContentConverter(JacksonConverter(laxObjectMapper)))
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
                withContext(coroutineContext + metricsDispatcher) {
                    call.respondText(Metrics.meterRegistry.scrape())
                }
            }
        }
    }
}
