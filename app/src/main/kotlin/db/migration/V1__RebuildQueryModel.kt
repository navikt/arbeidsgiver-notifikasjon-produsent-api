package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

@Suppress("unused") // flyway
class V2__RebuildQueryModel : BaseJavaMigration() {
    @Suppress("SqlWithoutWhere")
    override fun migrate(context: Context) {
        context.connection.createStatement().use {
            it.executeUpdate("delete from notifikasjon")
            it.executeUpdate("delete from brukerklikk")
            MigrationOps.resetOffsetsToEarliest()
        }
    }
}


