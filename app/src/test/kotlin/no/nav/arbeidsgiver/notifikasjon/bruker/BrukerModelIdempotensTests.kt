package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import no.nav.arbeidsgiver.notifikasjon.bruker.Bruker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class BrukerModelIdempotensTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    describe("Idempotent oppførsel") {
        forAll<Hendelse>(EksempelHendelse.Alle) { hendelse ->
            it("håndterer ${hendelse::class.simpleName} med idempotens") {
                queryModel.oppdaterModellEtterHendelse(hendelse)
                queryModel.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})