package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

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

object KafkaReaper {
    val databaseConfig = Database.config("kafka_reaper_model")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, configure = {
            connector {
                port = httpPort
            }
            shutdownGracePeriod = 20000
            shutdownTimeout = 30000
        }) {
            val hendelsesstrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "reaper-model-builder",
                replayPeriodically = true,
            )
            val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
            val database = openDatabaseAndSetReady(databaseConfig)
            launch {
                val kafkaReaperService = KafkaReaperServiceImpl(
                    KafkaReaperModelImpl(database),
                    hendelseProdusent
                )
                hendelsesstrøm.forEach { hendelse ->
                    kafkaReaperService.håndterHendelse(hendelse)
                }
            }

            configureRouting { }
            registerShutdownListener()
        }.start(wait = true)
    }
}
