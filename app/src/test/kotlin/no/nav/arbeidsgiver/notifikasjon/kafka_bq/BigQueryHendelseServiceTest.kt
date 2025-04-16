package no.nav.arbeidsgiver.notifikasjon.kafka_bq

import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.TableId
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.BigQueryClient
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import kotlin.test.Test
import kotlin.test.assertEquals

class BigQueryHendelseServiceTest {

    @Test
    fun `insertHendelse should store the correct row in the BigQuery Local Client`() {
        val datasetId = "myLocalDataset"
        val tableName = "myLocalTable"

        val localBigQueryClient = LocalBigQueryClient(datasetId)

        val bigQueryHendelseService = BigQueryHendelseService(
            bigQueryClient = localBigQueryClient,
            tableName = tableName
        )

        val testHendelse = EksempelHendelse.SakOpprettet

        bigQueryHendelseService.insertHendelse(testHendelse)

        val tableId = TableId.of(datasetId, tableName)
        val rows = localBigQueryClient.tableData[tableId]

        assertEquals(1, rows?.size)

        val insertedRow = rows?.first()
        assertEquals(testHendelse.hendelseId.toString(), insertedRow?.get("hendelseId"))

        val jsonData = insertedRow?.get("data") as String
        assertEquals(true, jsonData.contains(testHendelse.hendelseId.toString()))
    }
}

class LocalBigQueryClient(private val datasetId: String) : BigQueryClient {
    val tableData = mutableMapOf<TableId, MutableList<Map<String, Any>>>()

    override fun getOrCreateTable(tableName: String, schema: Schema): TableId {
        val tableId = TableId.of(datasetId, tableName)
        tableData.putIfAbsent(tableId, mutableListOf())
        return tableId
    }

    override fun insert(tableId: TableId, insertId: String, row: Map<String, Any>) {
        tableData[tableId]?.add(row)
    }
}