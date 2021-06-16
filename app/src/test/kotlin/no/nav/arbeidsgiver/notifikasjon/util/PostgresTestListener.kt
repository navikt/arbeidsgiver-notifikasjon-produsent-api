package no.nav.arbeidsgiver.notifikasjon.util

import db.migration.MigrationOps
import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database

fun TestConfiguration.testDatabase(): Database =
    runBlocking {
        mockkObject(MigrationOps)
        every { MigrationOps.resetOffsetsToEarliest() } returns Unit
        Database.openDatabase("bruker_model")
    }
        .also { listener(PostgresTestListener(it)) }

class PostgresTestListener(private val database: Database): TestListener {
    override val name: String
        get() = "PostgresTestListener"

    override suspend fun beforeSpec(spec: Spec) {
        database.withFlyway {
            clean()
            migrate()
        }
    }
}