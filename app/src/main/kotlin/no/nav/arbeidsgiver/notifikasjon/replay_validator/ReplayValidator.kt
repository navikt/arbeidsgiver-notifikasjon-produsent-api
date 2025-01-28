package no.nav.arbeidsgiver.notifikasjon.replay_validator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
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
        runBlocking(Dispatchers.Default) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                hendelsesstrøm.forEach { _ ->
                    // noop. implicitly validated
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
