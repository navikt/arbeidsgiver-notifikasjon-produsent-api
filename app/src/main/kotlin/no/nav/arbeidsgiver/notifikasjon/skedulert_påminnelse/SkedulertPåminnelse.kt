package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
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

object SkedulertPåminnelse {
    val databaseConfig = Database.config("skedulert_paaminnelse_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "skedulert-paaminnelse-2",
        )
    }

    fun main(httpPort: Int = 8080) {
        Health.subsystemReady[Subsystem.DATABASE] = true

        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val service = SkedulertPåminnelseService(
                hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC),
                database = database.await()
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
                delay(Duration.ofMinutes(1))
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}