package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import org.flywaydb.core.Flyway

class PostgresTestListener: TestListener {
    override val name: String
        get() = "PostgresTestListener"

    override suspend fun beforeTest(testCase: TestCase) {
        val flyway = Flyway.configure().dataSource(DB.dataSource).load()
        flyway.clean()
        flyway.migrate()
    }
}