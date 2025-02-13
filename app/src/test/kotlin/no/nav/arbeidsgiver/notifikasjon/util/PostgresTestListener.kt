package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

val templateDbs = ConcurrentHashMap<Database.Config, String>()
val ids = generateSequence(0) { it + 1 }.iterator()
val mutex = Mutex()
private suspend fun createDbFromTemplate(config: Database.Config, dbPrefix: String): Database.Config {
    val templateDb = templateDb(config, dbPrefix)
    val database = mutex.withLock {
        "${dbPrefix}-${ids.next()}"
    }

    DriverManager.getConnection(config.url.toString(), config.username, config.password).use { conn ->
        conn.createStatement().use { stmt ->
            @Suppress("SqlSourceToSinkFlow")
            stmt.executeUpdate("""create database "$database" template "$templateDb"; """)
        }
    }
    return config.withDatabase(database)
}

/**
 * henter template database for gitt config, eller lager en ny hvis den ikke finnes.
 * template databasen brukes til å lage ferske databaser for hver test.
 */
@Suppress("SqlSourceToSinkFlow")
private fun templateDb(config: Database.Config, dbPrefix: String): String {
    val templateDb = templateDbs.computeIfAbsent(config) {
        "${config.database}_template".also { db ->
            DriverManager.getConnection(config.url.toString(), config.username, config.password).use { conn ->
                conn.createStatement().use { stmt ->
                    val resultSet =
                        stmt.executeQuery("SELECT datname FROM pg_database where datname like '${dbPrefix}%';")
                    val tables = resultSet.use {
                        generateSequence {
                            if (resultSet.next()) resultSet.getString(1) else null
                        }.toList()
                    }
                    stmt.executeUpdate(
                        tables.joinToString("\n") { table ->
                            """drop database if exists "$table"; """
                        }
                    )
                }
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("""drop database if exists "$db"; """)
                    stmt.executeUpdate("""create database "$db" ; """)
                }
            }
            runBlocking {
                Database.openDatabase(
                    config = config.withDatabase(db),
                    flywayAction = {
                        migrate()
                    }
                ).close()
            }
        }
    }
    return templateDb
}

fun TestConfiguration.testDatabase(config: Database.Config, dbPrefix: String? = null): Database =
    runBlocking {
        val testConfig = createDbFromTemplate(config, dbPrefix ?: "${config.database}_test")
        Database.openDatabase(
            config = testConfig.copy(
                // https://github.com/flyway/flyway/issues/2323#issuecomment-804495818
                jdbcOpts = mapOf("preparedStatementCacheQueries" to "0"),
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
