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
        blockingIO {
            dataSource.connection.use { connection ->
                body(connection)
            }
        }

    suspend fun withFlyway(locations: String, body: Flyway.() -> Unit) {
        blockingIO {
            Flyway.configure()
                .locations(locations)
                .dataSource(dataSource)
                .load()
                .body()
        }
    }
}