package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_MOTTAKER_1
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_MOTTAKER_2
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_VIRKSOMHET_1
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.typeNavn
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.asMap
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
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

    describe("hard delete på sak sletter alt og kan replayes") {
        val (database, produsentModel) = setupEngine()
        val merkelapp = "idempotenstest"
        val grupperingsid = "gr-42"
        val sakId = uuid("42")
        val notifikasjonId = uuid("314")

        val hendelsesforløp = listOf(
            EksempelHendelse.SakOpprettet.copy(
                sakId = sakId,
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            },
            EksempelHendelse.BeskjedOpprettet.copy(
                sakId = sakId,
                notifikasjonId = notifikasjonId,
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            },
            HendelseModel.HardDelete(
                hendelseId = UUID.randomUUID(),
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                aggregateId = sakId,
                produsentId = "",
                kildeAppNavn = "",
                deletedAt = OffsetDateTime.now(),
                grupperingsid = grupperingsid,
                merkelapp = merkelapp,
            ).also {
                produsentModel.oppdaterModellEtterHendelse(it)
            }
        )

        assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)

        // replay events
        hendelsesforløp.forEach {
            produsentModel.oppdaterModellEtterHendelse(it)
        }

        assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)


    }
})

private suspend fun DescribeSpecContainerScope.assertDeleted(
    database: Database,
    sakId: UUID,
    notifikasjonId: UUID,
    grupperingsid: String,
    merkelapp: String
) {
    it("sak is deleted") {
        database.nonTransactionalExecuteQuery(
            """
                select * from sak where id = '${sakId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
    }

    it("notifikasjon is deleted") {
        database.nonTransactionalExecuteQuery(
            """
                select * from notifikasjon where grupperingsid = '${grupperingsid}' and merkelapp = '${merkelapp}'
            """
        ) {
            asMap()
        }.size shouldBe 0
    }

    it("mottaker sak is deleted") {
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_enkeltrettighet where notifikasjon_id = '${sakId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_ressurs where notifikasjon_id = '${sakId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
    }

    it("mottaker notifikasjon is deleted") {
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_enkeltrettighet where notifikasjon_id = '${notifikasjonId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_ressurs where notifikasjon_id = '${notifikasjonId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
    }
}

private fun DescribeSpec.setupEngine(): Pair<Database, ProdusentRepositoryImpl> {
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    return Pair(database, produsentModel)
}