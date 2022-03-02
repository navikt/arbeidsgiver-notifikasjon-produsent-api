package no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.io.Closeable
import java.sql.Connection
import javax.sql.DataSource

class NonBlockingDataSource<T>(
    private val dataSource: T
) where
    T: DataSource,
    T: Closeable
{

    fun close() {
        dataSource.close()
    }
    suspend fun <T> withConnection(body: suspend (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                body(connection)
            }
        }

    suspend fun withFlyway(locations: String, body: Flyway.() -> Unit) {
        blockingIO {
            val flyway = Flyway.configure()
                .locations(locations)
                .dataSource(dataSource)
                .load()
            flyway.body()
        }
    }
}