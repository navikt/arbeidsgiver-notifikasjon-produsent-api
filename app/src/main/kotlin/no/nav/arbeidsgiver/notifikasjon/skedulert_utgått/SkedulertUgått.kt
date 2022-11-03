package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration

object SkedulertUgått {
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            "skedulert-utgaatt-1",
            seekToBeginning = true,
        )
    }

    fun main(httpPort: Int = 8080) {
        Health.subsystemReady[Subsystem.DATABASE] = true

        val skedulertUtgåttService = SkedulertUtgåttService(
            lagKafkaHendelseProdusent()
        )

        runBlocking(Dispatchers.Default) {
            launch {
                hendelsesstrøm.forEach { hendelse ->
                    skedulertUtgåttService.processHendelse(hendelse)
                }
            }

            launchProcessingLoop("skedulert utgått processor", pauseAfterEach = Duration.ofSeconds(1)) {
                // TODO: wait to process until ajour
                skedulertUtgåttService.sendVedUtgåttFrist()
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}