package no.nav.arbeidsgiver.notifikasjon.manuelt_vedlikehold

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionAwareHendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent

object ManueltVedlikehold {
    private val hendelsesstrøm by lazy {
        PartitionAwareHendelsesstrøm(
            groupId = "manuelt-vedlikehold-1",
            initState = { ManueltVedlikeholdService(
                lagKafkaHendelseProdusent(),
                System.getenv("NAIS_CLIENT_ID")!!,
            ) },
            processEvent =
            { service: ManueltVedlikeholdService, event: HendelseModel.Hendelse ->
                service.processHendelse(event)
            },
            processingLoopAfterCatchup =
            { service: ManueltVedlikeholdService ->
                service.performHardDeletes()
            },
        )
    }
    fun main(httpPort: Int = 8080) {
        Health.subsystemReady[Subsystem.DATABASE] = true

        runBlocking(Dispatchers.Default) {
            launchHttpServer(httpPort = httpPort)

            launch {
                hendelsesstrøm.start()
            }
        }
    }
}