package no.nav.arbeidsgiver.notifikasjon

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource
import kotlin.reflect.KProperty

fun hikariConfig(): HikariConfig {
    return HikariConfig().apply {
        val host = System.getenv("DB_HOST") ?: "localhost"
        val port = System.getenv("DB_PORT") ?: "5432"
        val db = System.getenv("DB_DATABASE") ?: "postgres"

        jdbcUrl = "jdbc:postgresql://$host:$port/$db"
        username = System.getenv("DB_USERNAME") ?: "postgres"
        password = System.getenv("DB_PASSWORD") ?: "postgres"
        driverClassName = "org.postgresql.Driver"
    }
}

fun HikariConfig.test() {
    val connection = DriverManager.getConnection(jdbcUrl, username, password)
    val test = connection.createStatement()
    test.execute("select 1")
    test.close()
    connection.close()
}

fun hikariDatasource(): HikariDataSource {
    val hikariConfig = hikariConfig()
    return HikariDataSource(hikariConfig)
}

private val log = LoggerFactory.getLogger("DB")!!

object DB {
    val dataSource: DataSource by DataSourceDelegate()
    val connection: Connection
        get() = dataSource.connection
}

class DataSourceDelegate {
    var dataSource: DataSource
    operator fun getValue(thisRef: Any?, property: KProperty<*>): DataSource {
        return dataSource
    }

    init {
        var ds: DataSource?
        do {
            ds = kotlin.runCatching {
                hikariConfig().test()
                hikariDatasource()
            }.onFailure {
                log.info("venter på database connection. ")
                sleep(1000)
            }.getOrNull()
        } while (ds == null)
        dataSource = ds
    }
}

internal fun DataSource.migrate(): MigrateResult? {
    return Flyway.configure()
        .dataSource(this)
        .load()
        .migrate()
}

class UnhandeledTransactionRollback(msg: String, e: Throwable) : Exception(msg, e)

private fun <T> defaultRollback(e: Exception): T {
    throw UnhandeledTransactionRollback("no rollback function registered", e)
}

private fun <T> Connection.transaction(rollback: (e: Exception) -> T = ::defaultRollback, body: () -> T): T {
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

internal fun <T> DataSource.transaction(
    rollback: (e: Exception) -> T = ::defaultRollback,
    body: (c: Connection) -> T
): T =
    connection.use { c ->
        c.transaction(rollback) {
            body(c)
        }
    }