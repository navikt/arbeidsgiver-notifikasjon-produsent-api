package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.util.collections.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health.SubsystemImpl.Companion.alive
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health.SubsystemImpl.Companion.ready
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health.SubsystemImpl.Companion.report
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

object Health {
    val database: Subsystem by lazy {
        subsystem("database")
    }

    private val log = logger()

    private val subsystems = ConcurrentList<SubsystemImpl>()

    val alive: Boolean get() = subsystems.alive
    val ready: Boolean get() = subsystems.ready
    val report: String get() = subsystems.report

    fun subsystem(name: String): Subsystem =
        SubsystemImpl(name).also {
            subsystems.add(it)
        }

    interface Subsystem {
        fun isDead()
        fun isReady()
    }

    private class SubsystemImpl(private val name: String): Subsystem {
        private val alive = AtomicBoolean(true)
        private val ready = AtomicBoolean(false)

        override fun isDead() = alive.set(false)
        override fun isReady() = ready.set(true)

        override fun toString() = "{$name alive: $alive ready: $ready}"

        companion object {
            val List<SubsystemImpl>.alive: Boolean
                get() = all { it.alive.get() }

            val List<SubsystemImpl>.ready: Boolean
                get() = all { it.ready.get() }

            val List<SubsystemImpl>.report: String
                get() = joinToString(prefix = "[", separator = ", ", postfix = "]")
        }
    }

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
