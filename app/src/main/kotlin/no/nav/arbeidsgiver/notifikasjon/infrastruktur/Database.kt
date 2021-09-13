package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.NonBlockingDataSource
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

/** Encapsulate a DataSource, and expose it through an higher-level interface which
 * takes care of of cleaning up all resources, and where it's clear whether you
 * are running a single command or a transaction.
 */
class Database private constructor(
    private val config: Config,
    private val dataSource: NonBlockingDataSource
) {
    data class Config(
        val host: String,
        val port: String,
        val database: String,
        val username: String,
        val password: String,
        val migrationLocations: String,
    ) {
        val jdbcUrl: String
            get() = "jdbc:postgresql://$host:$port/$database"
    }

    companion object {
        private val log = logger()

        private fun Config.asHikariConfig(): HikariConfig {
            val config = this
            return HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.username
                password = config.password
                driverClassName = "org.postgresql.Driver"
                metricsTrackerFactory = PrometheusMetricsTrackerFactory()
                minimumIdle = 1
                maximumPoolSize = 2
                connectionTimeout = 10000
                idleTimeout = 10001
                maxLifetime = 30001
            }
        }

        suspend fun openDatabase(config: Config): Database {
            val hikariConfig = config.asHikariConfig()

            /* When the application runs in kubernetes and connects to the
             * database throught a cloudsql-proxy sidecar, we might be
             * ready to connect to the the database before the sidecare
             * is ready.
             */
            while (!hikariConfig.connectionPossible()) {
                delay(1000)
            }

            val dataSource = NonBlockingDataSource(HikariDataSource(hikariConfig))
            dataSource.withFlyway(config.migrationLocations) {
                migrate()
            }
            return Database(config, dataSource)
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
        @Language("PostgreSQL") sql: String,
        setup: ParameterSetters.() -> Unit = {},
        transform: ResultSet.() -> T
    ): List<T> =
        dataSource.withConnection {
            Transaction(this).runQuery(sql, setup, transform)
        }

    suspend fun nonTransactionalCommand(
        @Language("PostgreSQL") sql: String,
        setup: ParameterSetters.() -> Unit = {},
    ): Int = dataSource.withConnection {
        Transaction(this).executeCommand(sql, setup)
    }

    suspend fun <T> transaction(
        rollback: (e: Exception) -> T = { throw it },
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
        dataSource.withFlyway(config.migrationLocations, body)
    }
}

@JvmInline
value class Transaction(
    private val connection: Connection
) {
    fun <T> runQuery(
        @Language("PostgreSQL") sql: String,
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
        @Language("PostgreSQL") sql: String,
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

    fun timestamp_utc(value: OffsetDateTime) =
        timestamp(value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())

    fun timestamp_utc(value: Instant) {
        timestamp(LocalDateTime.ofInstant(value, ZoneOffset.UTC))
    }

    fun timestamp(value: LocalDateTime) =
        preparedStatement.setObject(index++, value)
}

