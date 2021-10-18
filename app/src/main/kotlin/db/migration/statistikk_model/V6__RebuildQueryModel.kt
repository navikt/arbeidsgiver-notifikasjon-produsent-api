package db.migration.statistikk_model

import db.migration.MigrationOps
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

@Suppress("unused") // flyway
class V6__RebuildQueryModel : BaseJavaMigration() {
    @Suppress("SqlWithoutWhere")
    override fun migrate(context: Context) {
        context.connection.createStatement().use {
            it.executeUpdate("delete from notifikasjon_statistikk")
            it.executeUpdate("delete from notifikasjon_statistikk_klikk")
            it.executeUpdate("delete from varsel_statistikk")
            MigrationOps.resetOffsetsToEarliest()
        }
    }
}


