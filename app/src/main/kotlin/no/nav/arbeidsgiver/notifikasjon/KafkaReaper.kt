package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaperModelImpl
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaperServiceImpl

object KafkaReaper {
    val databaseConfig = Database.config("kafka_reaper_model")
    private val hendelsesstrøm by lazy { HendelsesstrømKafkaImpl("reaper-model-builder") }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val reaperModelAsync = async {
                KafkaReaperModelImpl(database.await())
            }

            launch {
                val kafkaReaperService = KafkaReaperServiceImpl(
                    reaperModelAsync.await(),
                    lagKafkaHendelseProdusent()
                )
                hendelsesstrøm.forEach { hendelse ->
                    kafkaReaperService.håndterHendelse(hendelse)
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
