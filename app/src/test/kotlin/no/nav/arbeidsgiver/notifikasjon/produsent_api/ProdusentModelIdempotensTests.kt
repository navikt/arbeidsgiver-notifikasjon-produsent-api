package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class ProdusentModelIdempotensTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)

    describe("Idempotent oppførsel") {
        forAll<Hendelse>(EksempelHendelse.Alle) { hendelse ->
            it("håndterer ${hendelse::class.simpleName} med idempotens") {
                produsentModel.oppdaterModellEtterHendelse(hendelse)
                produsentModel.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})