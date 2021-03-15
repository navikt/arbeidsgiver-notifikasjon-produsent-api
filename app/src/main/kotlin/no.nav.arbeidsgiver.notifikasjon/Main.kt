package no.nav.arbeidsgiver.notifikasjon

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    println("hello world")

    embeddedServer(Netty, port = 8080) {
        routing {
            route("internal") {
                get ("alive") {
                    call.respond(HttpStatusCode.OK)
                }
                get ("ready") {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }.start(wait = true)
}