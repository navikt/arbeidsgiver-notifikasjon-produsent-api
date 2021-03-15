package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.metrics.micrometer.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.slf4j.event.Level
import java.util.*

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    embeddedServer(Netty, port = 8080) {
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
            exception<Throwable> { ex ->
                if (ex is JsonProcessingException) {
                    // ikke logg json-teksten som feilet.
                    ex.clearLocation()
                }

                log.warn("unhandle exception in ktor pipeline: {}", ex::class.qualifiedName, ex)
                call.respond(
                    HttpStatusCode.InternalServerError, mapOf(
                        "error" to "unexpected error",
                    )
                )
            }
        }

        routing {
            route("internal") {
                get ("alive") {
                    call.respond(HttpStatusCode.OK)
                }
                get ("ready") {
                    call.respond(HttpStatusCode.OK)
                }

                get("metrics") {
                    call.respond(meterRegistry.scrape())
                }
            }
        }
    }.start(wait = true)
}