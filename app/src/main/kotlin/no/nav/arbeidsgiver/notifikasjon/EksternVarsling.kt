package no.nav.arbeidsgiver.notifikasjon

import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.eksternvarsling.EksternVarslingRepository
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.consumer.ConsumerConfig


object EksternVarsling {
    val log = logger()

    val databaseConfig = Database.Config(
        host = System.getenv("DB_HOST") ?: "localhost",
        port = System.getenv("DB_PORT") ?: "5432",
        username = System.getenv("DB_USERNAME") ?: "postgres",
        password = System.getenv("DB_PASSWORD") ?: "postgres",
        database = System.getenv("DB_DATABASE") ?: "ekstern-varsling-model",
        migrationLocations = "db/migration/ekstern_varsling_model",
    )

    fun main(
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val eksternVarslingModelAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    EksternVarslingRepository(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            launch {
                // Hvorfor er det nyttig å kunne skru av? Brukes det?
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer {
                        put(ConsumerConfig.GROUP_ID_CONFIG, "ekstern-varsling-model-builder")
                    }
                    val eksternVarslingModel = eksternVarslingModelAsync.await()

                    kafkaConsumer.forEachEvent { event ->
                        eksternVarslingModel.oppdaterModellEtterHendelse(event)
                    }
                }
            }

            launch {
                // plukk oppgaver fra databasen
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
