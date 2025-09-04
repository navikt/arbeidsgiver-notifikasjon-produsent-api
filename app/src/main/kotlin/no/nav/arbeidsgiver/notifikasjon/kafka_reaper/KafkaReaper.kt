package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

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

object KafkaReaper {
    val databaseConfig = Database.config("kafka_reaper_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "reaper-model-builder",
            replayPeriodically = true,
        )
    }

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig)
            val reaperModelDeferred = async {
                KafkaReaperModelImpl(databaseDeferred.await())
            }

            launch {
                val kafkaReaperService = KafkaReaperServiceImpl(
                    reaperModelDeferred.await(),
                    lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
                )
                hendelsesstrøm.forEach { hendelse ->
                    kafkaReaperService.håndterHendelse(hendelse)
                }
            }

            configureRouting {  }
        }.start(wait = true)
    }
}
