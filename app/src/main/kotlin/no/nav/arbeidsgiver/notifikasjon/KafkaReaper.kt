package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaperModelImpl
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaperServiceImpl

object KafkaReaper {
    val log = logger()
    val databaseConfig = Database.config("kafka_reaper_model")

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(Health.database, databaseConfig)
            val reaperModelAsync = async {
                KafkaReaperModelImpl(database.await())
            }

            launch {
                val kafkaReaperService = KafkaReaperServiceImpl(
                    reaperModelAsync.await(),
                    createKafkaProducer()
                )
                forEachHendelse("reaper-model-builder") { hendelse ->
                    kafkaReaperService.håndterHendelse(hendelse)
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
