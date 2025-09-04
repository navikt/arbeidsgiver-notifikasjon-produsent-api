package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
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
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-paaminnelse-2",
        )
    }

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig)
            val service = SkedulertPåminnelseService(
                hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                database = databaseDeferred.await()
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