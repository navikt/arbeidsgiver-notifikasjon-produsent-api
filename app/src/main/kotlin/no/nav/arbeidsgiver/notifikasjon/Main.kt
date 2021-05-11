package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*

object Main {
    val log = logger()

    fun main() {
        runBlocking(Dispatchers.Default) {
            val dataSourceAsync = async {
                try {
                    Database.createDataSource().also {
                        Health.subsystemReady[Subsystem.DATABASE] = true
                    }
                } catch (e: Exception) {
                    Health.subsystemAlive[Subsystem.DATABASE] = false
                    throw e
                }
            }

            launch {
                val kafkaConsumer = createKafkaConsumer()
                val dataSource = dataSourceAsync.await()

                kafkaConsumer.forEachEvent { event ->
                    QueryModel.builderProcessor(dataSource, event)
                }
            }

            launch {
                val httpServer = embeddedServer(Netty, port = 8080, configure = {
                    connectionGroupSize = 16
                    callGroupSize = 16
                    workerGroupSize = 16
                }) {
                    httpServerSetup(
                        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
                            altinn = AltinnImpl,
                            dataSourceAsync = dataSourceAsync.asCompletableFuture(),
                            kafkaProducer = createKafkaProducer()
                        ),
                        produsentGraphQL = ProdusentAPI.newGraphQL(createKafkaProducer())
                    )
                }
                httpServer.start(wait = true)
            }
        }
    }
}

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    Main.main()
}

