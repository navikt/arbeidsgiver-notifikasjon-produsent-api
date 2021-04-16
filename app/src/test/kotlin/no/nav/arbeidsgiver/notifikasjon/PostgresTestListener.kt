package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class PostgresTestListener(private val dataSource: DataSource): TestListener {
    override val name: String
        get() = "PostgresTestListener"

    override suspend fun beforeTest(testCase: TestCase) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .apply {
                clean()
                migrate()
            }
    }
}