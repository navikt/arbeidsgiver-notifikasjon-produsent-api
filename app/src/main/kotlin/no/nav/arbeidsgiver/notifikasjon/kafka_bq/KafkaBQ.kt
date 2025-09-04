package no.nav.arbeidsgiver.notifikasjon.kafka_bq

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.BigQueryClientImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl

object KafkaBQ {
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(groupId = "kafka-bq-v1")
    }
    private val projectId = System.getenv("GCP_TEAM_PROJECT_ID")
        ?: error("Missing required environment variable: GCP_TEAM_PROJECT_ID")
    private val datasetId = System.getenv("BIGQUERY_DATASET_ID")
        ?: error("Missing required environment variable: BIGQUERY_DATASET_ID")
    private val tableName = System.getenv("BIGQUERY_TABLE_NAME")
        ?: error("Missing required environment variable: BIGQUERY_TABLE_NAME")

    private val bigQueryHendelseService = BigQueryHendelseService(
        tableName = tableName,
        bigQueryClient = BigQueryClientImpl(
            projectId = projectId,
            datasetId = datasetId
        ),
    )

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            Health.subsystemReady[Subsystem.DATABASE] = true

            launch {
                hendelsesstrøm.forEach { hendelse ->
                    bigQueryHendelseService.insertHendelse(hendelse)
                }
            }

            configureRouting { }
        }.start(wait = true)
    }
}