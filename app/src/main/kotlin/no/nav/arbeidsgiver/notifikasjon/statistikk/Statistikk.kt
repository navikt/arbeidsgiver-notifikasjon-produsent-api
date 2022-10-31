package no.nav.arbeidsgiver.notifikasjon.statistikk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Duration

object Statistikk {
    private val log = logger()
    val databaseConfig = Database.config("statistikk_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "statistikk-model-builder-1",
            replayPeriodically = true,
        )
    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)

            val statistikkServiceAsync = async {
                StatistikkServiceImpl(StatistikkModel(database.await()))
            }

            launch {
                val statistikkService = statistikkServiceAsync.await()
                hendelsesstrøm.forEach { hendelse, metadata ->
                    statistikkService.håndterHendelse(hendelse, metadata)
                }
            }

            launch {
                val statistikkService = statistikkServiceAsync.await()
                launchProcessingLoop(
                    "gauge-oppdatering",
                    pauseAfterEach = Duration.ofMinutes(1),
                ) {
                    statistikkService.updateGauges()
                    log.info("gauge-oppdatering vellykket ")
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
