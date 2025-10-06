package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

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

object SkedulertPåminnelse {
    val databaseConfig = Database.config("skedulert_paaminnelse_model")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val database = openDatabaseAndSetReady(databaseConfig)
            val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )

            val hendelsesstrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "skedulert-paaminnelse-2",
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
            registerShutdownListener()
        }.start(wait = true)
    }
}