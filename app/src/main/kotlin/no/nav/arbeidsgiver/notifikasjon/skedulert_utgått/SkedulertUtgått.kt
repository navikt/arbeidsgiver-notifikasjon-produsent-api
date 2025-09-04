package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration

object SkedulertUtgått {
    val databaseConfig = Database.config("skedulert_utgatt_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-utgatt-model-builder-0",
            replayPeriodically = true,
        )
    }

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig)
            val repositoryDeferred = async {
                SkedulertUtgåttRepository(databaseDeferred.await())
            }
            launch {
                val repo = repositoryDeferred.await()
                hendelsesstrøm.forEach { hendelse, _ ->
                    repo.oppdaterModellEtterHendelse(hendelse)
                }
            }

            val serviceDeferred = async {
                SkedulertUtgåttService(
                    repository = repositoryDeferred.await(),
                    hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
                )
            }
            launchProcessingLoop(
                "utgaatt-oppgaver-service",
                pauseAfterEach = Duration.ofMinutes(1)
            ) {
                serviceDeferred.await().settOppgaverUtgåttBasertPåFrist()
            }
            launchProcessingLoop(
                "avholdt-kalenderavtaler-service",
                pauseAfterEach = Duration.ofMinutes(1)
            ) {
                serviceDeferred.await().settKalenderavtalerAvholdtBasertPåTidspunkt()
            }

            configureRouting {  }
        }.start(wait = true)
    }
}