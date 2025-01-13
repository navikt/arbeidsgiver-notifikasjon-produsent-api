package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
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
            groupId = "skedulert-harddelete-model-builder-1",
            replayPeriodically = true,
        )
    }

    fun main(httpPort: Int = 8080) {
        Health.subsystemReady[Subsystem.DATABASE] = true

        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val repoAsync = async {
                SkedulertUtgåttRepository(database.await())
            }
            launch {
                val repo = repoAsync.await()
                hendelsesstrøm.forEach { hendelse, metadata ->
                    repo.oppdaterModellEtterHendelse(hendelse)
                }
            }

            val service = async {
                SkedulertUtgåttService(
                    repository = repoAsync.await(),
                    hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
                )
            }
            launchProcessingLoop(
                "utgaatt-oppgaver-service",
                pauseAfterEach = Duration.ofMinutes(1)
            ) {
                service.await().settOppgaverUtgåttBasertPåFrist()
            }
            launchProcessingLoop(
                "avholdt-kalenderavtaler-service",
                pauseAfterEach = Duration.ofMinutes(1)
            ) {
                service.await().settKalenderavtalerAvholdtBasertPåTidspunkt()
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}