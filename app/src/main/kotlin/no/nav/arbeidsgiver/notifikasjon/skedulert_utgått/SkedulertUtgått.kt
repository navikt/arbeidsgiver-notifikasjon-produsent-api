package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration

object SkedulertUtgått {
    val databaseConfig = Database.config("skedulert_utgatt_model")

    fun main(httpPort: Int = 8080) = runBlocking {
        val database = openDatabaseAsync(databaseConfig).await()
        val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
        val hendelsesstrøm = HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-utgatt-model-builder-0",
            replayPeriodically = true,
        )

        embeddedServer(CIO, port = httpPort) {
            val repository = SkedulertUtgåttRepository(database)
            val service = SkedulertUtgåttService(
                repository = repository,
                hendelseProdusent = hendelseProdusent
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