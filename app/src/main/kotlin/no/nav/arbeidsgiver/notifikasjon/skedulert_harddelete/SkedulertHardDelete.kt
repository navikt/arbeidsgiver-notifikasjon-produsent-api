package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

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
import java.time.Instant

object SkedulertHardDelete {
    val databaseConfig = Database.config("skedulert_harddelete_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-harddelete-model-builder-1",
            replayPeriodically = true,
        )
    }

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig)

            val repositoryDeferred = async {
                SkedulertHardDeleteRepositoryImpl(databaseDeferred.await())
            }
            launch {
                val repo = repositoryDeferred.await()
                hendelsesstrøm.forEach { hendelse, metadata ->
                    repo.oppdaterModellEtterHendelse(hendelse, metadata.timestamp)
                }
            }

            val service = async {
                SkedulertHardDeleteService(repositoryDeferred.await(), lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC))
            }
            launchProcessingLoop(
                "autoslett-service",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                service.await().sendSkedulerteHardDeletes(Instant.now())
            }

            configureRouting {  }
        }.start(wait = true)
    }
}
