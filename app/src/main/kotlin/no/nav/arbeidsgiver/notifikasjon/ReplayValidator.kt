package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import kotlin.collections.set

object ReplayValidator {
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "replay-validator",
            seekToBeginning = true,
            replayPeriodically = true,
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
