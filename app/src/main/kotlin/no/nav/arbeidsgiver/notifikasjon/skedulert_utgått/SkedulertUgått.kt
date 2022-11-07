package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionAwareHendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration

object SkedulertUgått {
    private val hendelsesstrøm by lazy {
        PartitionAwareHendelsesstrøm<SkedulertUtgåttService>(
            groupId = "skedulert-utgaatt-1",
            initState = { SkedulertUtgåttService(lagKafkaHendelseProdusent()) },
            processEvent =
            { service: SkedulertUtgåttService, event: HendelseModel.Hendelse ->
                service.processHendelse(event)
            },
            processingLoopAfterCatchup =
            { service: SkedulertUtgåttService ->
                coroutineScope {
                    launchProcessingLoop("skedulert utgått processor", pauseAfterEach = Duration.ofSeconds(1)) {
                        service.sendVedUtgåttFrist()
                    }
                }
            },
        )
    }

    fun main(httpPort: Int = 8080) {
        Health.subsystemReady[Subsystem.DATABASE] = true

        runBlocking(Dispatchers.Default) {
            launch {
                hendelsesstrøm.start()
            }
            launchHttpServer(httpPort = httpPort)
        }
    }
}