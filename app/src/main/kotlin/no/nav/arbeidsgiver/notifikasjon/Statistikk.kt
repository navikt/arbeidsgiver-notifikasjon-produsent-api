package no.nav.arbeidsgiver.notifikasjon

import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.statistikk.StatistikkModel
import no.nav.arbeidsgiver.notifikasjon.statistikk.AbacusServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.consumer.ConsumerConfig
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

object Statistikk {
    val log = logger()

    val databaseConfig = Database.Config(
        host = System.getenv("DB_HOST") ?: "localhost",
        port = System.getenv("DB_PORT") ?: "5432",
        username = System.getenv("DB_USERNAME") ?: "postgres",
        password = System.getenv("DB_PASSWORD") ?: "postgres",
        database = System.getenv("DB_DATABASE") ?: "statistikk-model",
        migrationLocations = "db/migration/statistikk_model",
    )

    @OptIn(ExperimentalTime::class)
    fun main(
        httpPort: Int = 8080
    ) {
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
                AbacusServiceImpl(
                    statistikkModelAsync.await()
                )
            }

            launch {
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer {
                        put(ConsumerConfig.GROUP_ID_CONFIG, "statistikk-model-builder")
                    }

                    val statistikkService = statistikkServiceAsync.await()

                    kafkaConsumer.forEachEvent { hendelse, metadata ->
                        statistikkService.håndterHendelse(hendelse, metadata)
                    }
                }
            }

            launch {
                val statistikkService = statistikkServiceAsync.await()
                while (true) {
                    statistikkService.updateGauges()
                    log.info("gauge-oppdatering vellykket ")
                    delay(Duration.minutes(1))
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
