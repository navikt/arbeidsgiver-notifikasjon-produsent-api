package no.nav.arbeidsgiver.notifikasjon.manuelt_vedlikehold

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionAwareHendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent

object ManueltVedlikehold {
    fun main(httpPort: Int = 8080) {
        val hendelsesstrøm = PartitionAwareHendelsesstrøm(
            groupId = "manuelt-vedlikehold-1",
            newPartitionProcessor = {
                ManueltVedlikeholdService(
                    lagKafkaHendelseProdusent(),
                    System.getenv("NAIS_CLIENT_ID")!!,
                )
            },
        )

        embeddedServer(CIO, configure = {
            connector {
                port = httpPort
            }
            shutdownGracePeriod = 20000
            shutdownTimeout = 30000
        }) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                hendelsesstrøm.start()
            }

            configureRouting {  }
            registerShutdownListener()
        }.start(wait = true)
    }
}