package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.autoslett.AutoSlettRepository
import no.nav.arbeidsgiver.notifikasjon.autoslett.AutoSlettService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse
import java.time.Duration
import java.time.Instant

object AutoSlett {
    val databaseConfig = Database.config("autoslett_model")

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)

            val repo = async {
                AutoSlettRepository(database.await())
            }
            launch {
                forEachHendelse("autoslett-model-builder") { hendelse, metadata ->
                    repo.await().oppdaterModellEtterHendelse(hendelse, metadata.timestamp)
                }
            }

            val service = async {
                AutoSlettService(repo.await(), createKafkaProducer())
            }
            launchProcessingLoop(
                "autoslett-service",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                service.await().slettDeSomSkalSlettes(Instant.now())
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
