package no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import javax.sql.DataSource

class NonBlockingDataSource(
    private val dataSource: DataSource
) {
    suspend fun <T> withConnection(body: suspend Connection.() -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                body(connection)
            }
        }

    suspend fun withFlyway(name: String, body: Flyway.() -> Unit) {
        withContext(Dispatchers.IO) {
            Flyway.configure()
                .locations("db/migration/$name")
                .dataSource(dataSource)
                .load()
                .body()
        }
    }
}