package no.nav.arbeidsgiver.notifikasjon

import io.ktor.application.*
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
                    call.respond(200)
                }
                get ("ready") {
                    call.respond(200)
                }
            }
        }
    }.start(wait = true)
}