package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant

class SkedulertHardDeleteIdempotensTests : DescribeSpec({
    val database = testDatabase(SkedulertHardDelete.databaseConfig)
    val repository = SkedulertHardDeleteRepositoryImpl(database)

    describe("SkedulertHardDelete Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
            repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
        }
    }
})
