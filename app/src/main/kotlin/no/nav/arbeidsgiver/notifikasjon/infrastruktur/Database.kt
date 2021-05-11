package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import javax.sql.DataSource

object Database {
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

    private fun HikariConfig.connectionPossible(): Boolean {
        log.info("attempting database connection")
        return try {
            DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                connection.createStatement().use { test ->
                    test.execute("select 1")
                }
            }
            log.info("attempting database connection: success")
            true
        } catch (e: Exception) {
            log.info("attempting database connection: fail with exception", e)
            false
        }
    }

    suspend fun createDataSource(hikariConfig: HikariConfig = DEFAULT_HIKARI_CONFIG): DataSource {
        while (!hikariConfig.connectionPossible()) {
            delay(1000)
        }

        return HikariDataSource(hikariConfig)
            .also { dataSource ->
                Flyway.configure()
                    .dataSource(dataSource)
                    .load()
                    .migrate()
            }
    }

    suspend fun <T> DataSource.useConnection(body: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            connection.use(body)
        }

    class UnhandeledTransactionRollback(msg: String, e: Throwable) : Exception(msg, e)

    private fun <T> defaultRollback(e: Exception): T {
        throw UnhandeledTransactionRollback("no rollback function registered", e)
    }

    private fun <T> Connection.transaction(rollback: (Exception) -> T = ::defaultRollback, body: () -> T): T {
        val savedAutoCommit = autoCommit
        autoCommit = false

        return try {
            val result = body()
            commit()
            result
        } catch (e: Exception) {
            rollback(e)
        } finally {
            autoCommit = savedAutoCommit
        }
    }

    suspend fun <T> DataSource.transaction(
        rollback: (e: Exception) -> T = ::defaultRollback,
        body: (c: Connection) -> T
    ): T =
        useConnection { c ->
            c.transaction(rollback) {
                body(c)
            }
        }

    inline fun <T> ResultSet.map(f: () -> T): List<T> {
        val list = ArrayList<T>()
        while (this.next()) {
            list.add(f())
        }
        return list
    }
}
