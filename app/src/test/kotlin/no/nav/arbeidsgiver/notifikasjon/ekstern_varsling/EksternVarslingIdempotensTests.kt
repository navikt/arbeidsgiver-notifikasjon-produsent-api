package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import no.nav.arbeidsgiver.notifikasjon.EksternVarsling
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class EksternVarslingIdempotensTests : DescribeSpec({
    val database = testDatabase(EksternVarsling.databaseConfig)
    val repository = EksternVarslingRepository(database)

    describe("Idempotent oppførsel") {
        forAll<Hendelse>(EksempelHendelse.Alle) { hendelse ->
            it("håndterer ${hendelse::class.simpleName} med idempotens") {
                repository.oppdaterModellEtterHendelse(hendelse)
                repository.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})
