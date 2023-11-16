package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionAwareHendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent

object SkedulertUtgått {
    private val hendelsesstrøm by lazy {
        PartitionAwareHendelsesstrøm(
            groupId = "skedulert-utgaatt-1",
            newPartitionProcessor = { SkedulertUtgåttService(hendelseProdusent = lagKafkaHendelseProdusent()) },
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