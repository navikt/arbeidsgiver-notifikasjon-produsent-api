package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import java.time.Duration
import java.time.Instant

object SkedulertHardDelete {
    val databaseConfig = Database.config("skedulert_harddelete_model")
    private val hendelsesstrøm by lazy { HendelsesstrømKafkaImpl(topic = NOTIFIKASJON_TOPIC, "skedulert-harddelete-model-builder") }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)

            val repoAsync = async {
                SkedulertHardDeleteRepository(database.await())
            }
            launch {
                val repo = repoAsync.await()
                hendelsesstrøm.forEach { hendelse, metadata ->
                    repo.oppdaterModellEtterHendelse(hendelse, metadata.timestamp)
                }
            }

            val service = async {
                SkedulertHardDeleteService(repoAsync.await(), lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC))
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
