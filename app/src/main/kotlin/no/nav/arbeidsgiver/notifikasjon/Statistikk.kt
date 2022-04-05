package no.nav.arbeidsgiver.notifikasjon

import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.statistikk.StatistikkModel
import no.nav.arbeidsgiver.notifikasjon.statistikk.StatistikkServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerConfig
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
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer {
                        put(ConsumerConfig.GROUP_ID_CONFIG, "statistikk-model-builder-1")
                    }

                    val statistikkService = statistikkServiceAsync.await()

                    kafkaConsumer.forEachEvent { hendelse, metadata ->
                        statistikkService.håndterHendelse(hendelse, metadata)
                    }
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
