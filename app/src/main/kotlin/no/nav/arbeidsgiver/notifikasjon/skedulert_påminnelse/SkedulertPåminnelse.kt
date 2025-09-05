package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import java.time.Duration

object SkedulertPåminnelse {
    val databaseConfig = Database.config("skedulert_paaminnelse_model")

    fun main(httpPort: Int = 8080) = runBlocking {
        val hendelsesstrøm = HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-paaminnelse-2",
        )
        val database = openDatabaseAsync(databaseConfig).await()
        val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)

        embeddedServer(CIO, port = httpPort) {
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )

            launch {
                hendelsesstrøm.forEach { hendelse, _ ->
                    service.processHendelse(hendelse)
                }
            }

            launchProcessingLoop(
                "sendAktuellePåminnelser",
                pauseAfterEach = Duration.ofMinutes(10)
            ) {
                service.sendAktuellePåminnelser()
            }

            configureRouting {  }
        }.start(wait = true)
    }
}