package no.nav.arbeidsgiver.notifikasjon.autoslett

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import no.nav.arbeidsgiver.notifikasjon.AutoSlett
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant

class AutoSlettIdempotensTests : DescribeSpec({
    val database = testDatabase(AutoSlett.databaseConfig)
    val repository = AutoSlettRepository(database)

    describe("Idempotent oppførsel") {
        forAll<HendelseModel.Hendelse>(EksempelHendelse.Alle) { hendelse ->
            it("håndterer ${hendelse::class.simpleName} med idempotens") {
                repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
                repository.oppdaterModellEtterHendelse(hendelse, Instant.EPOCH)
            }
        }
    }
})
