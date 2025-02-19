package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.google.cloud.bigquery.*

interface BigQueryClient {
    fun getOrCreateTable(tableName: String, schema: Schema): TableId
    fun insert(tableId: TableId, insertId: String, row: Map<String, Any>)
}

class BigQueryClientImpl(
    projectId: String,
    private val datasetId: String,
) : BigQueryClient {
    private val bigQuery = BigQueryOptions.newBuilder()
        .setProjectId(projectId)
        .build()
        .service

    override fun getOrCreateTable(tableName: String, schema: Schema): TableId {
        val tableId = TableId.of(datasetId, tableName)
        val existingTable = bigQuery.getTable(tableId)

        return if (existingTable != null) {
            tableId
        } else {
            createTable(tableId, schema)
        }
    }

    private fun createTable(tableId: TableId, schema: Schema): TableId {
        val tableDefinition = StandardTableDefinition.of(schema)
        val tableInfo = TableInfo.of(tableId, tableDefinition)
        return bigQuery.create(tableInfo).tableId
    }

    override fun insert(tableId: TableId, insertId: String, row: Map<String, Any>) {
        val insertResponse = bigQuery.getTable(tableId).insert(
            listOf(InsertAllRequest.RowToInsert.of(insertId, row))
        )

        if (insertResponse.hasErrors()) {
            throw RuntimeException("Lagring i BigQuery feilet: '${insertResponse.insertErrors}'")
        }
    }
}