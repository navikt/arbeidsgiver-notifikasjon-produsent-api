package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.typeNavn
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.*

class ProdusentModelIdempotensTests : DescribeSpec({

    describe("Produsent Model Idempotent oppførsel") {
        context("alle hendelser to ganger") {
            val (_, produsentModel) = setupEngine()

            withData(EksempelHendelse.Alle) { hendelse ->
                produsentModel.oppdaterModellEtterHendelse(hendelse)
                produsentModel.oppdaterModellEtterHendelse(hendelse)
            }
        }


        context("NyBeskjed to ganger") {
            val (database, produsentModel) = setupEngine()
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

        /**
         * Dette skal egentlig ikke skje, men pga en race condition har det skjedd i dev-gcp.
         * Kan også skje i prod. Resultatet burde være at produsent får fornuftig feilmelding, men pga race condition
         * blir hendelsene opprettet på kafka og det siste kallet feiler med
         * Exception while fetching data (/nySak) : ERROR: insert or update on table "sak_id" violates foreign key constraint "sak_id_sak_id_fkey"
         * Dette burde egentlig blitt kommunisert som Duplikatgrupperingsid til produsent.
         */
        context("sak opprettet med forskjellig virksomhetsnummer men samme merkelapp og grupperingsid") {
            val (_, produsentModel) = setupEngine()
            val sakId1 = UUID.randomUUID()
            val sakId2 = UUID.randomUUID()
            produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.SakOpprettet.copy(
                sakId = sakId1,
                virksomhetsnummer = "42"
            ))
            produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.SakOpprettet.copy(
                sakId = sakId2,
                virksomhetsnummer = "44"
            ))

            it("kun en sak opprettes") {
                produsentModel.hentSak(sakId1) shouldNotBe null
                produsentModel.hentSak(sakId2) shouldBe null
            }
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                val (_, produsentModel) = setupEngine()
                produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.HardDelete.copy(
                    virksomhetsnummer = hendelse.virksomhetsnummer,
                    aggregateId = hendelse.aggregateId,
                ))
                produsentModel.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})

private fun DescribeSpec.setupEngine(): Pair<Database, ProdusentRepositoryImpl> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    return Pair(database, produsentModel)
}