package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_MOTTAKER_1
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_MOTTAKER_2
import no.nav.arbeidsgiver.notifikasjon.bruker.TEST_VIRKSOMHET_1
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.asMap
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProdusentModelIdempotensTest {

    @Test
    fun `alle hendelser to ganger`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)

        EksempelHendelse.Alle.forEach { hendelse ->
            produsentModel.oppdaterModellEtterHendelse(hendelse)
            produsentModel.oppdaterModellEtterHendelse(hendelse)
        }
    }


    @Test
    fun `NyBeskjed to ganger`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
        produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)
        produsentModel.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)

        // ingen duplikat mottaker
        assertEquals(
            1,
            database.nonTransactionalExecuteQuery(
                """
            select * from mottaker_altinn_enkeltrettighet
            where notifikasjon_id = '${EksempelHendelse.BeskjedOpprettet.notifikasjonId}'
        """
            ) { }.size
        )
    }

    /**
     * Dette skal egentlig ikke skje, men pga en race condition har det skjedd i dev-gcp.
     * Kan også skje i prod. Resultatet burde være at produsent får fornuftig feilmelding, men pga race condition
     * blir hendelsene opprettet på kafka og det siste kallet feiler med
     * Exception while fetching data (/nySak) : ERROR: insert or update on table "sak_id" violates foreign key constraint "sak_id_sak_id_fkey"
     * Dette burde egentlig blitt kommunisert som Duplikatgrupperingsid til produsent.
     */
    @Test
    fun `sak opprettet med forskjellig virksomhetsnummer men samme merkelapp og grupperingsid`() =
        withTestDatabase(Produsent.databaseConfig) { database ->
            val produsentModel = ProdusentRepositoryImpl(database)
            val sakId1 = UUID.randomUUID()
            val sakId2 = UUID.randomUUID()
            produsentModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    sakId = sakId1,
                    virksomhetsnummer = "42"
                )
            )
            produsentModel.oppdaterModellEtterHendelse(
                EksempelHendelse.SakOpprettet.copy(
                    sakId = sakId2,
                    virksomhetsnummer = "44"
                )
            )

            // kun en sak opprettes
            assertNotNull(produsentModel.hentSak(sakId1))
            assertNull(produsentModel.hentSak(sakId2))
        }

    @Test
    fun `Håndterer partial replay hvor midt i hendelsesforløp etter harddelete`() {
        EksempelHendelse.Alle.forEach { hendelse ->
            withTestDatabase(Produsent.databaseConfig) { database ->

                val produsentModel = ProdusentRepositoryImpl(database)
                produsentModel.oppdaterModellEtterHendelse(
                    EksempelHendelse.HardDelete.copy(
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        aggregateId = hendelse.aggregateId,
                    )
                )
                produsentModel.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }

    @Test
    fun `hard delete på sak sletter alt og kan replayes`() = withTestDatabase(Produsent.databaseConfig) { database ->
        val produsentModel = ProdusentRepositoryImpl(database)
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

            assertDeleted(database, sakId, notifikasjonId, grupperingsid, merkelapp)


        }
    }
}

private suspend fun assertDeleted(
    database: Database,
    sakId: UUID,
    notifikasjonId: UUID,
    grupperingsid: String,
    merkelapp: String
) {
    // sak is deleted
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            """
                select * from sak where id = '${sakId}'
            """
        ) { asMap() }.size
    )

    // notifikasjon is deleted
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            """
                select * from notifikasjon where grupperingsid = '${grupperingsid}' and merkelapp = '${merkelapp}'
            """
        ) { asMap() }.size
    )

    // mottaker sak is deleted
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_enkeltrettighet where notifikasjon_id = '${sakId}'
            """
        )
        { asMap() }.size
    )

    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_ressurs where notifikasjon_id = '${sakId}'
            """
        ) { asMap() }.size
    )

    // mottaker notifikasjon is deleted
    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_enkeltrettighet where notifikasjon_id = '${notifikasjonId}'
            """
        ) { asMap() }.size
    )

    assertEquals(
        0,
        database.nonTransactionalExecuteQuery(
            """
                select * from mottaker_altinn_ressurs where notifikasjon_id = '${notifikasjonId}'
            """
        ) { asMap() }.size
    )
}

