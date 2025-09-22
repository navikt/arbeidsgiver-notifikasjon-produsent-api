package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

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
import java.time.Instant

object SkedulertHardDelete {
    val databaseConfig = Database.config("skedulert_harddelete_model")

    fun main(httpPort: Int = 8080) = runBlocking {
        val hendelsesstrøm = HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-harddelete-model-builder-1",
            replayPeriodically = true,
        )
        val database = openDatabaseAsync(databaseConfig).await()
        val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)

        embeddedServer(CIO, port = httpPort) {
            val repository = SkedulertHardDeleteRepositoryImpl(database)
            val service = SkedulertHardDeleteService(repository, hendelseProdusent)

            launch {
                hendelsesstrøm.forEach { hendelse, metadata ->
                    repository.oppdaterModellEtterHendelse(hendelse, metadata.timestamp)
                }
            }

            launchProcessingLoop(
                "autoslett-service",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                service.sendSkedulerteHardDeletes(Instant.now())
            }

            configureRouting {  }
            registerShutdownListener()
            hendelsesstrøm.registerShutdownListener(this)
        }.start(wait = true)
    }
}
