package no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
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

    suspend fun withFlyway(
        locations: String,
        config: FluentConfiguration.() -> Unit = {},
        action: Flyway.() -> Unit
    ) {
        blockingIO {
            Flyway.configure()
                .locations(locations)
                .dataSource(dataSource)
                .apply(config)
                .load()
                .action()
        }
    }
}