package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class Subsystem {
    DATABASE
}

object Health {
    private val log = logger()

    val subsystemAlive = ConcurrentHashMap(mapOf(
        Subsystem.DATABASE to true
    ))

    val alive
        get() = subsystemAlive.all { it.value }

    val subsystemReady = ConcurrentHashMap(mapOf(
        Subsystem.DATABASE to false
    ))

    val ready
        get() = subsystemReady.all { it.value }

    private val terminatingAtomic = AtomicBoolean(false)

    val terminating: Boolean
        get() = terminatingAtomic.get()

    init {
        val shutdownTimeout = basedOnEnv(
            prod = { Duration.ofSeconds(20) },
            dev = { Duration.ofSeconds(20) },
            other = { Duration.ofMillis(0) },
        )

        Runtime.getRuntime().addShutdownHook(object: Thread() {
            override fun run() {
                terminatingAtomic.set(true)
                log.info("shutdown signal received")
                try {
                    sleep(shutdownTimeout.toMillis())
                } catch (e: Exception) {
                    // nothing to do
                }
            }
        })
    }
}