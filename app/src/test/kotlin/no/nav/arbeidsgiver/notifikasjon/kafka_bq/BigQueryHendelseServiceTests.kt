package no.nav.arbeidsgiver.notifikasjon.kafka_bq

import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.TableId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.BigQueryClient
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse

class BigQueryHendelseServiceTest : FunSpec({

    test("insertHendelse should store the correct row in the BigQuery Local Client") {
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

        rows?.size shouldBe 1

        val insertedRow = rows?.first()
        insertedRow?.get("hendelseId") shouldBe testHendelse.hendelseId.toString()

        val jsonData = insertedRow?.get("data") as String
        jsonData.contains(testHendelse.hendelseId.toString()) shouldBe true
    }
})

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