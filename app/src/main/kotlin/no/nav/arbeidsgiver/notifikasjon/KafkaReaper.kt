package no.nav.arbeidsgiver.notifikasjon

import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaperModelImpl
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaperServiceImpl
import org.apache.kafka.clients.consumer.ConsumerConfig

object KafkaReaper {
    val log = logger()
    val databaseConfig = Database.config("kafka_reaper_model")

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val reaperModelAsync = async {
                try {
                    val database = Database.openDatabase(databaseConfig)
                    Health.subsystemReady[Subsystem.DATABASE] = true
                    KafkaReaperModelImpl(database)
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            launch {
                if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
                    log.info("KafkaConsumer er deaktivert.")
                } else {
                    val kafkaConsumer = createKafkaConsumer {
                        put(ConsumerConfig.GROUP_ID_CONFIG, "reaper-model-builder")
                    }

                    val kafkaReaperService = KafkaReaperServiceImpl(
                        reaperModelAsync.await(),
                        createKafkaProducer()
                    )

                    kafkaConsumer.forEachEvent { hendelse ->
                        kafkaReaperService.håndterHendelse(hendelse)
                    }
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
