package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseTidspunkt
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.getInstant
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SakOppdateresTest {


    private suspend fun Database.hentSakSisteEndretTidspunktById(sakId: UUID) = nonTransactionalExecuteQuery(
        """
            select sist_endret_tidspunkt
            from sak
            where id = ?
        """.trimIndent(),
        { uuid(sakId) },
        { getInstant("sist_endret_tidspunkt") }
    ).first()

    @Test
    fun `Sak oppdateres på OppgaveOpprettet`() = withTestDatabase(Bruker.databaseConfig, SakOppdateresTest::class.simpleName) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val sakOpprettet = brukerRepository.sakOpprettet(
            merkelapp = "merkelapp",
            oppgittTidspunkt = OffsetDateTime.now().minusDays(7),
        )
        val oppgave = brukerRepository.oppgaveOpprettet(
            sak = sakOpprettet,
            opprettetTidspunkt = sakOpprettet.oppgittTidspunkt!!.plusDays(1)
        )
        assertEquals(oppgave.opprettetTidspunkt.toInstant(), database.hentSakSisteEndretTidspunktById(sakOpprettet.sakId))
    }

    @Test
    fun `Sak oppdateres på BeskjedOpprettet`() = withTestDatabase(Bruker.databaseConfig, SakOppdateresTest::class.simpleName) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val sakOpprettet = brukerRepository.sakOpprettet(
            merkelapp = "merkelapp",
            oppgittTidspunkt = OffsetDateTime.now().minusDays(7),
        )
        val beskjed = brukerRepository.beskjedOpprettet(
            sak = sakOpprettet,
            opprettetTidspunkt = sakOpprettet.oppgittTidspunkt!!.plusDays(1)
        )
        val sistEndretTidspunkt = database.hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
        assertEquals(beskjed.opprettetTidspunkt.toInstant(), sistEndretTidspunkt)
    }

    @Test
    fun `Sak oppdateres ikke på OppgaveOpprettet me opprettetTidspunkt før sist_endret_tidspunkt`() = withTestDatabase(Bruker.databaseConfig, SakOppdateresTest::class.simpleName) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val sakOpprettet = brukerRepository.sakOpprettet(
            merkelapp = "merkelapp",
            oppgittTidspunkt = OffsetDateTime.now().minusDays(7),
        )
        brukerRepository.oppgaveOpprettet(
            sak = sakOpprettet,
            opprettetTidspunkt = sakOpprettet.oppgittTidspunkt!!.minusDays(1)
        )
        val sistEndretTidspunkt = database.hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
        assertEquals(sakOpprettet.oppgittTidspunkt!!.toInstant(), sistEndretTidspunkt)
    }

    @Test
    fun `Sak oppdateres på KalenderavtaleOpprettet`() = withTestDatabase(Bruker.databaseConfig, SakOppdateresTest::class.simpleName) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val sakOpprettet = brukerRepository.sakOpprettet(
            merkelapp = "merkelapp",
            oppgittTidspunkt = OffsetDateTime.now().minusDays(7),
        )
        val kalenderavtale = brukerRepository.kalenderavtaleOpprettet(
            sak = sakOpprettet,
            opprettetTidspunkt = sakOpprettet.oppgittTidspunkt!!.plusDays(1)
        )
        val sistEndretTidspunkt = database.hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
        assertEquals(kalenderavtale.opprettetTidspunkt.toInstant(), sistEndretTidspunkt)
    }

    @Test
    fun `Sak oppdateres på NyStatusSak`() = withTestDatabase(Bruker.databaseConfig, SakOppdateresTest::class.simpleName) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val sakOpprettet = brukerRepository.sakOpprettet(
            merkelapp = "merkelapp",
            oppgittTidspunkt = OffsetDateTime.now().minusDays(7),
        )
        val statusSak = brukerRepository.nyStatusSak(
            sak = sakOpprettet,
            idempotensKey = "nei",
            oppgittTidspunkt = sakOpprettet.oppgittTidspunkt!!.plusDays(1)
        )
        val sistEndretTidspunkt = database.hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
        assertEquals(statusSak.opprettetTidspunkt.toInstant(), sistEndretTidspunkt)
    }

    @Test
    fun `Sak oppdateres på PåminnelseOpprettet`()  = withTestDatabase(Bruker.databaseConfig, SakOppdateresTest::class.simpleName) { database ->
        val brukerRepository = BrukerRepositoryImpl(database)
        val sakOpprettet = brukerRepository.sakOpprettet(
            merkelapp = "merkelapp",
            oppgittTidspunkt = OffsetDateTime.now().minusDays(7),
        )
        val oppgave = brukerRepository.oppgaveOpprettet(
            sak = sakOpprettet,
            opprettetTidspunkt = sakOpprettet.oppgittTidspunkt!!.plusDays(1)
        )
        val påminnelsesTidspunkt = oppgave.opprettetTidspunkt.plusDays(7)
        val påminnelse = brukerRepository.påminnelseOpprettet(
            oppgave,
            tidspunkt = PåminnelseTidspunkt.createAndValidateKonkret(
                påminnelsesTidspunkt.toLocalDateTime(),
                oppgave.opprettetTidspunkt,
                null,
                null
            )
        )
        val sistEndretTidspunkt = database.hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
        assertEquals(påminnelse.tidspunkt.påminnelseTidspunkt, sistEndretTidspunkt)
    }
}

