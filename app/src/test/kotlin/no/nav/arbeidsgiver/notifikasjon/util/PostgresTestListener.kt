package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

val ids = generateSequence() { UUID.randomUUID() }.iterator()
fun TestConfiguration.testDatabase(config: Database.Config): Database =
    runBlocking {
        val db = "${config.database}_${ids.next()}"
        try {
            DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("""create database "$db"; """)
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        Database.openDatabase(
            config.copy(
                // https://github.com/flyway/flyway/issues/2323#issuecomment-804495818
                jdbcOpts = mapOf("preparedStatementCacheQueries" to 0),
                port = "1337",
                database = db,
            )
        )
    }
        .also { listener(PostgresTestListener(it)) }

class PostgresTestListener(private val database: Database): TestListener {

    override suspend fun beforeContainer(testCase: TestCase) {
        database.withFlyway({
            cleanDisabled(false)
        }) {
            clean()
            migrate()
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        database.close()
    }
}