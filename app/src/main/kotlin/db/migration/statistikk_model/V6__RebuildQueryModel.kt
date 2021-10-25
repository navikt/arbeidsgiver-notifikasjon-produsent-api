package db.migration.statistikk_model

import db.migration.MigrationOps
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

@Suppress("unused") // flyway
class V6__RebuildQueryModel : BaseJavaMigration() {
    override fun migrate(context: Context) {
        context.connection.createStatement().use {
            MigrationOps.resetOffsetsToEarliest()
        }
    }
}


