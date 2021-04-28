package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Main")!!

val objectMapper = jacksonObjectMapper().apply {
    setDefaultPrettyPrinter(
        DefaultPrettyPrinter().apply {
            indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
            indentObjectsWith(DefaultIndenter("  ", "\n"))
        }
    )
    configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    registerModule(JavaTimeModule())
}

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    runBlocking(Dispatchers.Default) {
        val dataSourceAsync = async {
            try {
                createDataSource().also {
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
                queryModelBuilderProcessor(dataSource, event)
            }
        }

        launch {
            val httpServer = embeddedServer(CIO, port = 8080) {
                httpServerSetup(
                    brukerGraphQL = createBrukerGraphQL(
                        altinn = AltinnImpl,
                        dataSourceAsync = dataSourceAsync.asCompletableFuture()
                    ),
                    produsentGraphQL = createProdusentGraphQL(createKafkaProducer())
                )
            }
            httpServer.start(wait = true)
        }
    }
}
