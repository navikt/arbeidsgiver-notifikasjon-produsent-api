package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

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

object KafkaReaper {
    val databaseConfig = Database.config("kafka_reaper_model")

    fun main(httpPort: Int = 8080) = runBlocking {
        val hendelsesstrøm = HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "reaper-model-builder",
            replayPeriodically = true,
        )
        val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
        val database = openDatabaseAsync(databaseConfig).await()

        embeddedServer(CIO, port = httpPort) {
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
            hendelsesstrøm.registerShutdownListener(this)
        }.start(wait = true)
    }
}
