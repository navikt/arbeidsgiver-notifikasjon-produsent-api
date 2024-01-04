package no.nav.arbeidsgiver.notifikasjon.replay_validator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionAwareHendelsesstrøm
import kotlin.collections.set

object ReplayValidator {
    private val hendelsesstrøm by lazy {
        PartitionAwareHendelsesstrøm(
            groupId = "replay-validator",
            replayPeriodically = true,
            newPartitionProcessor = { ReplayValidatorService() },
        )
    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                hendelsesstrøm.start()
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
