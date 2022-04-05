package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database

fun TestConfiguration.testDatabase(config: Database.Config): Database =
    runBlocking {
        Database.openDatabase(
            config.copy(
                // https://github.com/flyway/flyway/issues/2323#issuecomment-804495818
                jdbcOpts = mapOf("preparedStatementCacheQueries" to 0)

            )
        )
    }
        .also { listener(PostgresTestListener(it)) }

class PostgresTestListener(private val database: Database): TestListener {
    override val name: String
        get() = "PostgresTestListener"

    override suspend fun beforeContainer(testCase: TestCase) {
        database.withFlyway {
            clean()
            migrate()
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        database.close()
    }
}