package no.nav.arbeidsgiver.notifikasjon.kafka_bq

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.TableId
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.BigQueryClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper

class BigQueryHendelseService(
    private val bigQueryClient: BigQueryClient,
    private val tableName: String,
) {
    private val tableId: TableId by lazy {
        bigQueryClient.getOrCreateTable(tableName, schema)
    }

    private val schema = Schema.of(
        Field.of("hendelseId", StandardSQLTypeName.STRING),
        Field.of("aggregateId", StandardSQLTypeName.STRING),
        Field.of("virksomhetsnummer", StandardSQLTypeName.STRING),
        Field.of("data", StandardSQLTypeName.JSON)
    )

    fun insertHendelse(hendelse: HendelseModel.Hendelse) {
        val row = mapOf(
            "hendelseId" to hendelse.hendelseId.toString(),
            "aggregateId" to hendelse.aggregateId.toString(),
            "virksomhetsnummer" to hendelse.virksomhetsnummer,
            "data" to laxObjectMapper.writeValueAsString(hendelse)
        )
        bigQueryClient.insert(tableId, hendelse.hendelseId.toString(), row)
    }
}