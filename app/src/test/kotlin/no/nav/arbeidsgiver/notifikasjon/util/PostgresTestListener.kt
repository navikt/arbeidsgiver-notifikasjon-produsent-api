package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val templateDbs = ConcurrentHashMap<Database.Config, String>()
val ids = generateSequence() { UUID.randomUUID() }.iterator()
private suspend fun createDbFromTemplate(config: Database.Config): Database.Config {
    val templateDb = templateDb(config)
    val database = "${config.database}_${ids.next()}".also { db ->
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use { conn ->
            conn.createStatement().use { stmt ->
                @Suppress("SqlSourceToSinkFlow")
                stmt.executeUpdate("""create database "$db" template "$templateDb"; """)
            }
        }
    }
    return config.copy(database = database)
}

/**
 * henter template database for gitt config, eller lager en ny hvis den ikke finnes.
 * template databasen brukes til å lage ferske databaser for hver test.
 */
@Suppress("SqlSourceToSinkFlow")
private suspend fun templateDb(config: Database.Config): String {
    val templateDb = templateDbs.computeIfAbsent(config) {
        "${config.database}_template".also { db ->
            DriverManager.getConnection(config.jdbcUrl, config.username, config.password).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("""drop database if exists "$db"; """)
                    stmt.executeUpdate("""create database "$db" ; """)
                }
            }
            runBlocking {
                Database.openDatabase(
                    config = config.copy(
                        port = "1337",
                        database = db,
                    ),
                    flywayAction = {
                        migrate()
                    }
                ).close()
            }
        }
    }
    return templateDb
}

fun TestConfiguration.testDatabase(config: Database.Config): Database =
    runBlocking {
        val database = createDbFromTemplate(config).database
        Database.openDatabase(
            config = config.copy(
                // https://github.com/flyway/flyway/issues/2323#issuecomment-804495818
                jdbcOpts = mapOf("preparedStatementCacheQueries" to 0),
                port = "1337",
                database = database,
            ),
            flywayAction = {
                /* noop. created from template. */
            }
        )
    }.also { listener(PostgresTestListener(it)) }

class PostgresTestListener(private val database: Database) : TestListener {

    override suspend fun beforeContainer(testCase: TestCase) {
    }

    override suspend fun afterSpec(spec: Spec) {
        database.close()
    }
}
