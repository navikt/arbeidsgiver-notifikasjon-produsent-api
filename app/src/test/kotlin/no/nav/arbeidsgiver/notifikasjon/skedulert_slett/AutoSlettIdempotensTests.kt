package no.nav.arbeidsgiver.notifikasjon.skedulert_slett

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant

class AutoSlettIdempotensTests : DescribeSpec({
    val database = testDatabase(SkedulertSlett.databaseConfig)
    val repository = SkedulertSlettRepository(database)

    describe("AutoSlett Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
            repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
        }
    }
})
