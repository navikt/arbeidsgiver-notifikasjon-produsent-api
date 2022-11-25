package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

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

object SkedulertPåminnelse {
    private val hendelsesstrøm by lazy {
        PartitionAwareHendelsesstrøm<SkedulertPåminnelseService>(
            groupId = "skedulert-paaminnelse-1",
            initState = { SkedulertPåminnelseService(lagKafkaHendelseProdusent()) },
            processEvent =
            { service: SkedulertPåminnelseService, event: HendelseModel.Hendelse ->
                service.processHendelse(event)
            },
            processingLoopAfterCatchup =
            { service: SkedulertPåminnelseService ->
                coroutineScope {
                    launchProcessingLoop("skedulert påminnelse processor", pauseAfterEach = Duration.ofSeconds(1)) {
                        service.sendAktuellePåminnelser()
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