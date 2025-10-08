package no.nav.arbeidsgiver.notifikasjon.replay_validator

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl

object ReplayValidator {
    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, configure = {
            connector {
                port = httpPort
            }
            shutdownGracePeriod = 20000
            shutdownTimeout = 30000
        }) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            val hendelsesstrøm = HendelsesstrømKafkaImpl(
                groupId = "replay-validator",
                replayPeriodically = true,
                seekToBeginning = true
            )

            launch {
                hendelsesstrøm.forEach { _ ->
                    // noop. implicitly validated
                }
            }

            configureRouting { }
            registerShutdownListener()
        }.start(wait = true)
    }
}
