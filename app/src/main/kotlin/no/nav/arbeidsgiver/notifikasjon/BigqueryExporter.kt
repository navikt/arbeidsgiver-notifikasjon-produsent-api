package no.nav.arbeidsgiver.notifikasjon

import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.QueryParameterValue
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.installMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.internalRoutes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.lang.RuntimeException
import java.time.Instant
import java.util.*

object BigqueryExporter {
    val log = logger()

    private const val DATASET = "notifikasjon"
    private const val TABLE = "hendelser"
    private const val DELETE_QUERY = """
        DELETE FROM `$DATASET.$TABLE` 
        WHERE aggregate_id = @aggregate_id
    """

    fun main(
        httpPort: Int = 8080
    ) {

        val bigquery = BigQueryOptions.newBuilder()
            .setProjectId(System.getenv("GCP_TEAM_PROJECT_ID"))
            .build()
            .service


        fun delete(aggregateId: UUID) {
            bigquery.query(
                QueryJobConfiguration.newBuilder(DELETE_QUERY)
                    .addNamedParameter("aggregate_id", QueryParameterValue.string(aggregateId.toString()))
                    .build()
            )
        }

        fun insert(hendelse: HendelseModel.Hendelse, kafkaTimestamp: Instant) {
            val row = InsertAllRequest.RowToInsert.of(
                    hendelse.hendelseId.toString(),
                    mapOf(
                        "timestamp" to kafkaTimestamp.toString(),
                        "key" to hendelse.hendelseId.toString(),
                        "aggregate_id" to hendelse.aggregateId.toString(),
                        "event" to laxObjectMapper.writeValueAsString(hendelse),
                    )
                )

            val response = bigquery.insertAll(
                InsertAllRequest.of(DATASET, TABLE, listOf(row))
            )

            if (response.hasErrors()) {
                throw RuntimeException("Insertion failed: ${response.insertErrors}")
            }
        }

        runBlocking(Dispatchers.Default) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                val kafkaConsumer = createKafkaConsumer {
                    put(ConsumerConfig.GROUP_ID_CONFIG, "bigquery-exporter")
                }

                kafkaConsumer.seekToBeginningOnAssignment()
                kafkaConsumer.forEachEvent { hendelse, metadata ->
                    if (hendelse is HendelseModel.HardDelete) {
                        delete(hendelse.aggregateId)
                    } else {
                        insert(hendelse, metadata.timestamp)
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
