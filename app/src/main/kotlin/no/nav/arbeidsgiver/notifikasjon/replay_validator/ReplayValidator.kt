package no.nav.arbeidsgiver.notifikasjon.replay_validator

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl

object ReplayValidator {
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            groupId = "replay-validator",
            replayPeriodically = true,
            seekToBeginning = true
        )
    }

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                hendelsesstrøm.forEach { _ ->
                    // noop. implicitly validated
                }
            }

            configureRouting {  }
        }.start(wait = true)
    }
}
