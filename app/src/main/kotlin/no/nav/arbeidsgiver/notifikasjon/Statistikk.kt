package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.statistikk.StatistikkModel
import no.nav.arbeidsgiver.notifikasjon.statistikk.StatistikkServiceImpl
import java.time.Duration
import kotlin.time.ExperimentalTime

object Statistikk {
    val log = logger()
    val databaseConfig = Database.config("statistikk_model")

    @OptIn(ExperimentalTime::class)
    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(Health.database, databaseConfig)

            val statistikkServiceAsync = async {
                StatistikkServiceImpl(StatistikkModel(database.await()))
            }

            launch {
                val statistikkService = statistikkServiceAsync.await()
                forEachHendelse("statistikk-model-builder-1") { hendelse, metadata ->
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

            launchHttpServer(httpPort)
        }
    }
}
