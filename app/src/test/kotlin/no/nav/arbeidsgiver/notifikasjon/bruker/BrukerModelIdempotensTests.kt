package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.typeNavn
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.asMap
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime
import java.util.*

class BrukerModelIdempotensTests : DescribeSpec({

    describe("BrukerModel Idempotent oppførsel") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        withData(EksempelHendelse.Alle) { hendelse ->
            brukerRepository.oppdaterModellEtterHendelse(hendelse)
            brukerRepository.oppdaterModellEtterHendelse(hendelse)
        }

    }
    describe("NyBeskjed to ganger") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)

        brukerRepository.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)
        brukerRepository.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)

        it("ingen duplikat mottaker") {
            val antallMottakere = database.nonTransactionalExecuteQuery(
                """
            select * from mottaker_altinn_tilgang
            where notifikasjon_id = '${EksempelHendelse.BeskjedOpprettet.notifikasjonId}'
        """
            ) {
            }.size
            antallMottakere shouldBe 1
        }
    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                val database = testDatabase(Bruker.databaseConfig)
                val brukerRepository = BrukerRepositoryImpl(database)
                brukerRepository.oppdaterModellEtterHendelse(
                    EksempelHendelse.HardDelete.copy(
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        aggregateId = hendelse.aggregateId,
                    )
                )
                brukerRepository.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }

    describe("hard delete på sak sletter alt og kan replayes") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val merkelapp = "idempotenstest"
        val grupperingsid = "gr-42"
        val sakId = uuid("42")
        val notifikasjonId = uuid("314")

        val hendelsesforløp = listOf(
            brukerRepository.sakOpprettet(
                sakId = sakId,
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
            ),
            brukerRepository.beskjedOpprettet(
                sakId = sakId,
                notifikasjonId = notifikasjonId,
                virksomhetsnummer = TEST_VIRKSOMHET_1,
                merkelapp = merkelapp,
                grupperingsid = grupperingsid,
                mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
            ),
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
                brukerRepository.oppdaterModellEtterHendelse(it)
            }
        )

        assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)

        // replay events
        hendelsesforløp.forEach {
            brukerRepository.oppdaterModellEtterHendelse(it)
        }

        assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)
    }

    describe("soft delete på sak sletter alt og kan replayes uten sakOpprettet") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val merkelapp = "idempotenstest"
        val grupperingsid = "gr-42"
        val sakId = uuid("42")
        val notifikasjonId = uuid("314")

        val sakOpprettet = brukerRepository.sakOpprettet(
            sakId = sakId,
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
            mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
        )

        val hendelsesforløp = listOf(
            sakOpprettet,
            brukerRepository.nyStatusSak(sakOpprettet, idempotensKey = "idempotensKey"),
            brukerRepository . beskjedOpprettet (
            sakId = sakId,
            notifikasjonId = notifikasjonId,
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            merkelapp = merkelapp,
            grupperingsid = grupperingsid,
            mottakere = listOf(TEST_MOTTAKER_1, TEST_MOTTAKER_2),
        ),
        HendelseModel.SoftDelete(
            hendelseId = UUID.randomUUID(),
            virksomhetsnummer = TEST_VIRKSOMHET_1,
            aggregateId = sakId,
            produsentId = "",
            kildeAppNavn = "",
            deletedAt = OffsetDateTime.now(),
            grupperingsid = grupperingsid,
            merkelapp = merkelapp,
        ).also {
            brukerRepository.oppdaterModellEtterHendelse(it)
        }
        )

        assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)

        // replay events utenom sakOpprettet
        hendelsesforløp.drop(1).forEach {
            brukerRepository.oppdaterModellEtterHendelse(it)
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
                select * from mottaker_altinn_tilgang where sak_id = '${sakId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
    }

    it("mottaker notifikasjon is deleted") {
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_tilgang where notifikasjon_id = '${notifikasjonId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
    }
    it("sak_status is deleted"){
        database.nonTransactionalExecuteQuery(
            """
                select * from sak_status where sak_id = '${notifikasjonId}'
            """
        ) {
            asMap()
        }.size shouldBe 0
    }
}