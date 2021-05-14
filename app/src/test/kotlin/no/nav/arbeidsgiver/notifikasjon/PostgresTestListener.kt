package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database

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