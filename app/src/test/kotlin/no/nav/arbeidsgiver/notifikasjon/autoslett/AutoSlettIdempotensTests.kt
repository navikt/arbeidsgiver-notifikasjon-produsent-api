package no.nav.arbeidsgiver.notifikasjon.autoslett

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant

class AutoSlettIdempotensTests : DescribeSpec({
    val database = testDatabase(AutoSlett.databaseConfig)
    val repository = AutoSlettRepository(database)

    describe("Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            it("håndterer ${hendelse::class.simpleName} med idempotens") {
                repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
                repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
            }
        }
    }
})
