package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAndSetReady
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration

object SkedulertUtgått {
    val databaseConfig = Database.config("skedulert_utgatt_model")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, configure = {
            connector {
                port = httpPort
            }
            shutdownGracePeriod = 20000
            shutdownTimeout = 30000
        }) {
            val database = openDatabaseAndSetReady(databaseConfig)
            val repository = SkedulertUtgåttRepository(database)
            val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
            val service = SkedulertUtgåttService(
                repository = repository,
                hendelseProdusent = hendelseProdusent
            )

            val hendelsesstrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "skedulert-utgatt-model-builder-0",
                replayPeriodically = true,
            )

            launch {
                hendelsesstrøm.forEach { hendelse, _ ->
                    repository.oppdaterModellEtterHendelse(hendelse)
                }
            }

            launchProcessingLoop(
                "utgaatt-oppgaver-service",
                pauseAfterEach = Duration.ofMinutes(1)
            ) {
                service.settOppgaverUtgåttBasertPåFrist()
            }
            launchProcessingLoop(
                "avholdt-kalenderavtaler-service",
                pauseAfterEach = Duration.ofMinutes(1)
            ) {
                service.settKalenderavtalerAvholdtBasertPåTidspunkt()
            }

            configureRouting { }
            registerShutdownListener()
        }.start(wait = true)
    }
}