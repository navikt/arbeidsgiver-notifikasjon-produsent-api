package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.NonBlockingDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

/** Encapsulate a DataSource, and expose it through an higher-level interface which
 * takes care of of cleaning up all resources, and where it's clear whether you
 * are running a single command or a transaction.
 */
class Database private constructor(
    private val dataSource: NonBlockingDataSource
) {
    companion object {
        private val log = logger()

        private val DEFAULT_HIKARI_CONFIG = HikariConfig().apply {
            val host = System.getenv("DB_HOST") ?: "localhost"
            val port = System.getenv("DB_PORT") ?: "5432"
            val db = System.getenv("DB_DATABASE") ?: "postgres"

            jdbcUrl = "jdbc:postgresql://$host:$port/$db"
            username = System.getenv("DB_USERNAME") ?: "postgres"
            password = System.getenv("DB_PASSWORD") ?: "postgres"
            driverClassName = "org.postgresql.Driver"
            metricsTrackerFactory = PrometheusMetricsTrackerFactory()
            minimumIdle = 1
            maximumPoolSize = 2
            connectionTimeout = 10000
            idleTimeout = 10001
            maxLifetime = 30001
        }

        suspend fun openDatabase(hikariConfig: HikariConfig = DEFAULT_HIKARI_CONFIG): Database {
            /* When the application runs in kubernetes and connects to the
             * database throught a cloudsql-proxy sidecar, we might be
             * ready to connect to the the database before the sidecare
             * is ready.
             */
            while (!hikariConfig.connectionPossible()) {
                delay(1000)
            }

            val dataSource = NonBlockingDataSource(HikariDataSource(hikariConfig))
            dataSource.withFlyway {
                migrate()
            }
            return Database(dataSource)
        }

        private suspend fun HikariConfig.connectionPossible(): Boolean {
            log.info("attempting database connection")
            return try {
                withContext(Dispatchers.IO) {
                    DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute("select 1")
                        }
                    }
                }
                log.info("attempting database connection: success")
                true
            } catch (e: Exception) {
                log.info("attempting database connection: fail with exception", e)
                false
            }
        }
    }

    suspend fun <T> runNonTransactionalQuery(
        sql: String,
        setup: ParameterSetters.() -> Unit = {},
        transform: ResultSet.() -> T
    ): List<T> =
        dataSource.withConnection {
            Transaction(this).runQuery(sql, setup, transform)
        }

    suspend fun nonTransactionalCommand(
        sql: String,
        setup: ParameterSetters.() -> Unit = {},
    ): Int = dataSource.withConnection {
        Transaction(this).executeCommand(sql, setup)
    }

    suspend fun <T> transaction(
        rollback: (e: Exception) -> T,
        body: Transaction.() -> T
    ): T =
        dataSource.withConnection {
            val savedAutoCommit = autoCommit
            autoCommit = false

            try {
                val result = Transaction(this).body()
                commit()
                result
            } catch (e: Exception) {
                rollback(e)
            } finally {
                autoCommit = savedAutoCommit
                close()
            }
        }

    suspend fun withFlyway(body: Flyway.() -> Unit) {
        dataSource.withFlyway(body)
    }

}


@JvmInline
value class Transaction(
    private val connection: Connection
) {
    fun <T> runQuery(
        sql: String,
        setup: ParameterSetters.() -> Unit = {},
        transform: ResultSet.() -> T
    ): List<T> {
        return connection
            .prepareStatement(sql)
            .use { preparedStatement ->
                ParameterSetters(preparedStatement).apply(setup)
                preparedStatement.executeQuery().use { resultSet ->
                    val resultList = mutableListOf<T>()
                    while (resultSet.next()) {
                        resultList.add(resultSet.transform())
                    }
                    resultList
                }
            }
    }

    fun executeCommand(
        sql: String,
        setup: ParameterSetters.() -> Unit = {},
    ): Int {
        return connection
            .prepareStatement(sql)
            .use { preparedStatement ->
                ParameterSetters(preparedStatement).apply(setup)
                preparedStatement.executeUpdate()
            }
    }
}

class ParameterSetters(
    private val preparedStatement: PreparedStatement
) {
    private var index = 1

    fun string(value: String) =
        preparedStatement.setString(index++, value)

    fun nullableString(value: String?) =
        preparedStatement.setString(index++, value)

    fun uuid(value: UUID) =
        preparedStatement.setObject(index++, value)

    fun timestamptz(value: OffsetDateTime) =
        preparedStatement.setObject(index++, value)
}

