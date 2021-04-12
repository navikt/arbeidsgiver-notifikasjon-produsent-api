package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("Main")!!
val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

enum class Checks {
    DATABASE
}
val livenessGauge = mutableMapOf(
    Checks.DATABASE to true,
)
val readinessGauge = mutableMapOf(
    Checks.DATABASE to false,
)

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
    GlobalScope.launch {
        createConsumer().processSingle(::queryModelBuilderProcessor)
    }
    GlobalScope.launch {
        var dataSource: DataSource?
        do {
            dataSource = try {
                DB.dataSource
            } catch(e: Exception) {
                log.info("venter på database connection. ")
                delay(250)
                null
            }
        } while (dataSource == null)

        try {
            dataSource.migrate()
            readinessGauge[Checks.DATABASE] = true
        } catch(e: Exception)  {
            log.error("migrering feilet", e)
            livenessGauge[Checks.DATABASE] = false
        }
    }
    embeddedServer(Netty, port = 8080, module = Application::module)
        .start(wait = true)
}
