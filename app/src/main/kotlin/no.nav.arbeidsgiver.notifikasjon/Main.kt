package no.nav.arbeidsgiver.notifikasjon

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.metrics.micrometer.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    embeddedServer(Netty, port = 8080) {
        install(MicrometerMetrics) {
            registry = meterRegistry
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