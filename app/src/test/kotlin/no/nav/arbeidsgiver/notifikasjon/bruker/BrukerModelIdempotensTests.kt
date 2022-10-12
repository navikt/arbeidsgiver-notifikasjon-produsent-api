package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class BrukerModelIdempotensTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    describe("BrukerModel Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            queryModel.oppdaterModellEtterHendelse(hendelse)
            queryModel.oppdaterModellEtterHendelse(hendelse)
        }
    }
})