package no.nav.arbeidsgiver.notifikasjon.replay_validator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionAwareHendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration
import kotlin.collections.set
import kotlin.time.Duration.Companion.seconds

object ReplayValidator {
    private val services = mutableListOf<ReplayValidatorService>()
    private val hendelsesstrøm by lazy {
        PartitionAwareHendelsesstrøm(
            groupId = "replay-validator",
            replayPeriodically = true,
            newPartitionProcessor = { ReplayValidatorService(services) },
        )
    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                hendelsesstrøm.start()
            }

            launchProcessingLoop(
                "replay-validator-update-gauge",
                init = { delay(10.seconds) },
                pauseAfterEach = Duration.ofMinutes(1),
            ) {
                services.toList().forEach(ReplayValidatorService::updateMetrics)
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
