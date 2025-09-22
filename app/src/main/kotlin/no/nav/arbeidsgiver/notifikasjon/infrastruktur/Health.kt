package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class Subsystem {
    DATABASE,
    AUTOSLETT_SERVICE,
    HARDDELETE_SERVICE,
    KAFKA,
    KTOR,
}

object Health {
    val subsystemAlive = ConcurrentHashMap(mapOf(
        Subsystem.DATABASE to true,
        Subsystem.KAFKA to true,
        Subsystem.KTOR to true,
        Subsystem.AUTOSLETT_SERVICE to true,
        Subsystem.HARDDELETE_SERVICE to true,
    ))

    val alive
        get() = subsystemAlive.all { it.value }

    val subsystemReady = ConcurrentHashMap(mapOf(
        Subsystem.DATABASE to false,
    ))

    val ready
        get() = subsystemReady.all { it.value }

    private val terminatingAtomic = AtomicBoolean(false)

    val terminating: Boolean
        get() = !alive || terminatingAtomic.get()

    fun terminate() {
        terminatingAtomic.set(true)
    }
}