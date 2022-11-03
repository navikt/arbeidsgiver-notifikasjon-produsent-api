package no.nav.arbeidsgiver.notifikasjon.skedulert_slett

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import java.time.Duration
import java.time.Instant

object SkedulertSlett {
    val databaseConfig = Database.config("autoslett_model")
    private val hendelsesstrøm by lazy { HendelsesstrømKafkaImpl("autoslett-model-builder") }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)

            val repoAsync = async {
                SkedulertSlettRepository(database.await())
            }
            launch {
                val repo = repoAsync.await()
                hendelsesstrøm.forEach { hendelse, metadata ->
                    repo.oppdaterModellEtterHendelse(hendelse, metadata.timestamp)
                }
            }

            val service = async {
                SkedulertSlettService(repoAsync.await(), lagKafkaHendelseProdusent())
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
