package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid

class ProdusentModelIdempotensTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)

    describe("Produsent Model Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            produsentModel.oppdaterModellEtterHendelse(hendelse)
            produsentModel.oppdaterModellEtterHendelse(hendelse)
        }


        context("NyBeskjed to ganger") {
            produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)
            produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)

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
    }
})