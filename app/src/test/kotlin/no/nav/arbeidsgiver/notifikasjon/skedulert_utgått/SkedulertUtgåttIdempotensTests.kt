package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class SkedulertUtgåttIdempotensTests : DescribeSpec({
    describe("SkedulertUtgått Idempotent oppførsel") {
        val repo = SkedulertUtgåttRepository(
            testDatabase(SkedulertUtgått.databaseConfig)
        )

        withData(EksempelHendelse.Alle) { hendelse ->
            repo.oppdaterModellEtterHendelse(hendelse)
            repo.oppdaterModellEtterHendelse(hendelse)
        }
    }
})
