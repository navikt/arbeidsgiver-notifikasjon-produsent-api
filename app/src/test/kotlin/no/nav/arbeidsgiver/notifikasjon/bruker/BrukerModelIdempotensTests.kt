package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
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

    describe("BrukerModel Idempotent oppførsel NyBeskjed to ganger") {
        queryModel.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)
        queryModel.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)

        it("ingen duplikat mottaker") {
            val antallMottakere = database.nonTransactionalExecuteQuery("""
            select * from mottaker_altinn_enkeltrettighet
            where notifikasjon_id = '${EksempelHendelse.BeskjedOpprettet.notifikasjonId}'
        """
            ) {
            }.size
            antallMottakere shouldBe 1
        }
    }
})