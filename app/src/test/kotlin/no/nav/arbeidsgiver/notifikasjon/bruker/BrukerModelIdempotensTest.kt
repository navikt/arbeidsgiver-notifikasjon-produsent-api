package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.asMap
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BrukerModelIdempotensTest {

    @Test
    fun `BrukerModel Idempotent oppførsel`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        EksempelHendelse.Alle.forEach { hendelse ->
            brukerRepository.oppdaterModellEtterHendelse(hendelse)
            brukerRepository.oppdaterModellEtterHendelse(hendelse)
        }
    }

    @Test
    fun `NyBeskjed to ganger`() = withTestDatabase(Bruker.databaseConfig) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)

        brukerRepository.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)
        brukerRepository.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)

        val antallMottakere = database.nonTransactionalExecuteQuery(
            """
            select * from mottaker_altinn_tilgang
            where notifikasjon_id = '${EksempelHendelse.BeskjedOpprettet.notifikasjonId}'
        """
        ) {
        }.size
        assertEquals(1, antallMottakere)
    }

    @Test
    fun `Håndterer partial replay hvor midt i hendelsesforløp etter harddelete`() {
        EksempelHendelse.Alle.forEach { hendelse ->
            withTestDatabase(Bruker.databaseConfig) { database ->
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

    @Test
    fun `hard delete på sak sletter alt og kan replayes`() = withTestDatabase(Bruker.databaseConfig) { database ->
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

    @Test
    fun `soft delete på sak sletter alt og kan replayes uten sakOpprettet`() = withTestDatabase(Bruker.databaseConfig) { database ->
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
            brukerRepository.beskjedOpprettet(
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
}


private suspend fun assertDeleted(
    database: Database,
    sakId: UUID,
    notifikasjonId: UUID,
    grupperingsid: String,
    merkelapp: String
) {
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            "select * from sak where id = '${sakId}'"
        ) { asMap() }.size
    )
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            "select * from notifikasjon where grupperingsid = '${grupperingsid}' and merkelapp = '${merkelapp}'"
        ) { asMap() }.size
    )
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            "select * from mottaker_altinn_tilgang where sak_id = '${sakId}'"
        ) { asMap() }.size
    )
    assertEquals(
        0,

        database.nonTransactionalExecuteQuery(
            "select * from mottaker_altinn_tilgang where notifikasjon_id = '${notifikasjonId}'"
        ) { asMap() }.size
    )
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            "select * from sak_status where sak_id = '${notifikasjonId}'"
        ) { asMap() }.size
    )
}