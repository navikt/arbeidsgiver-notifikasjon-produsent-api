package no.nav.arbeidsgiver.notifikasjon

import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
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
            val statistikkModelAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    StatistikkModel(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            val statistikkServiceAsync = async {
                StatistikkServiceImpl(
                    statistikkModelAsync.await()
                )
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

            launch {
                embeddedServer(Netty, port = httpPort) {
                    installMetrics()
                    routing {
                        internalRoutes()
                    }
                }.start(wait = true)
            }
        }
    }
}
