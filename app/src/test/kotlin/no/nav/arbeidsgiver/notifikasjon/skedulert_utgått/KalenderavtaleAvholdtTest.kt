package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class KalenderavtaleAvholdtTest {
    private val localDateTimeNow = LocalDateTime.now()
    private val tidspunktSomHarPassert = localDateTimeNow.minusHours(1)
    private val tidspunktSomIkkeHarPassert = localDateTimeNow.plusHours(1)

    private val sakOpprettet = HendelseModel.SakOpprettet(
        hendelseId = uuid("1"),
        virksomhetsnummer = "1",
        produsentId = "",
        kildeAppNavn = "",
        sakId = uuid("1"),
        grupperingsid = "42",
        merkelapp = "test",
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        tittel = "test",
        tilleggsinformasjon = null,
        lenke = "#test",
        oppgittTidspunkt = OffsetDateTime.now(),
        mottattTidspunkt = OffsetDateTime.now(),
        nesteSteg = null,
        hardDelete = null,
    )
    private val opprettet = HendelseModel.KalenderavtaleOpprettet(
        virksomhetsnummer = sakOpprettet.virksomhetsnummer,
        merkelapp = sakOpprettet.merkelapp,
        eksternId = "42",
        mottakere = sakOpprettet.mottakere,
        hendelseId = uuid("2"),
        notifikasjonId = uuid("2"),
        tekst = "test",
        lenke = "#test",
        opprettetTidspunkt = OffsetDateTime.now(),
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = "42",
        eksterneVarsler = listOf(),
        hardDelete = null,
        sakId = sakOpprettet.sakId,
        påminnelse = null,
        tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
        startTidspunkt = localDateTimeNow,
        sluttTidspunkt = null,
        lokasjon = null,
        erDigitalt = false,
    )
    private val oppdatert = HendelseModel.KalenderavtaleOppdatert(
        virksomhetsnummer = sakOpprettet.virksomhetsnummer,
        merkelapp = sakOpprettet.merkelapp,
        hendelseId = uuid("3"),
        notifikasjonId = uuid("2"),
        tekst = "test",
        lenke = "#test",
        opprettetTidspunkt = Instant.now(),
        oppdatertTidspunkt = opprettet.opprettetTidspunkt.toInstant(),
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = "42",
        eksterneVarsler = listOf(),
        hardDelete = null,
        påminnelse = null,
        tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
        startTidspunkt = localDateTimeNow,
        sluttTidspunkt = null,
        lokasjon = null,
        erDigitalt = false,
        idempotenceKey = null,
    )

    @Test
    fun `noop når starttidspunkt ikke har passert enda`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val (repo, hendelseProdusent, service) = setupTestApp(database)

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomIkkeHarPassert))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        assertEquals(emptyList(), hendelseProdusent.hendelser)
    }

    @Test
    fun `noop når aggregat er fjernet`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val (repo, hendelseProdusent, service) = setupTestApp(database)

        listOf(
            HendelseModel.HardDelete(
                aggregateId = opprettet.aggregateId,
                virksomhetsnummer = opprettet.virksomhetsnummer,
                hendelseId = UUID.randomUUID(),
                produsentId = opprettet.virksomhetsnummer,
                kildeAppNavn = opprettet.virksomhetsnummer,
                deletedAt = OffsetDateTime.now(),
                grupperingsid = null,
                merkelapp = opprettet.merkelapp,
            ),
            opprettet.copy(startTidspunkt = tidspunktSomHarPassert),
            oppdatert.copy(startTidspunkt = tidspunktSomHarPassert),
        ).forEach {
            repo.oppdaterModellEtterHendelse(it)
            service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
            assertEquals(emptyList(), hendelseProdusent.hendelser)
        }
    }

    @Test
    fun `noop når status er avholdt elelr avlyst`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val (repo, hendelseProdusent, service) = setupTestApp(database)

        repo.oppdaterModellEtterHendelse(
            opprettet.copy(
                notifikasjonId = uuid("11"),
                startTidspunkt = tidspunktSomHarPassert,
                tilstand = AVLYST
            )
        )
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
        assertEquals(emptyList(), hendelseProdusent.hendelser)

        repo.oppdaterModellEtterHendelse(
            opprettet.copy(
                notifikasjonId = uuid("22"),
                startTidspunkt = tidspunktSomHarPassert,
                tilstand = AVHOLDT
            )
        )
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
        assertEquals(emptyList(), hendelseProdusent.hendelser)
    }

    @Test
    fun `Skedulerer avholdt når starttidspunkt har passert`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val (repo, hendelseProdusent, service) = setupTestApp(database)

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomHarPassert))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser.first().also {
            it as HendelseModel.KalenderavtaleOppdatert
            assertEquals(AVHOLDT, it.tilstand)
        }
    }

    @Test
    fun `Skedulerer avholdt når starttidspunkt har passert og det finnes et starttidspunkt på kø som ikke har passert`() =
        withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
            /**
             * denne testen passer på at vi klarer å skille på forskjellige aggregater
             */

            val (repo, hendelseProdusent, service) = setupTestApp(database)

            repo.oppdaterModellEtterHendelse(
                opprettet.copy(
                    notifikasjonId = uuid("11"),
                    startTidspunkt = tidspunktSomIkkeHarPassert
                )
            )
            repo.oppdaterModellEtterHendelse(
                opprettet.copy(
                    notifikasjonId = uuid("22"),
                    startTidspunkt = tidspunktSomHarPassert
                )
            )
            service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

            assertEquals(1, hendelseProdusent.hendelser.size)
            hendelseProdusent.hendelser.first().also {
                it as HendelseModel.KalenderavtaleOppdatert
                assertEquals(AVHOLDT, it.tilstand)
                assertEquals(uuid("22"), it.aggregateId)
            }
        }

    @Test
    fun `skedulering fjernes når kalenderavtale setter til slutt tilstand`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val (repo, hendelseProdusent, service) = setupTestApp(database)

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomHarPassert))
        repo.oppdaterModellEtterHendelse(oppdatert.copy(tilstand = AVLYST))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        assertEquals(emptyList(), hendelseProdusent.hendelser)

        repo.oppdaterModellEtterHendelse(
            opprettet.copy(
                notifikasjonId = uuid("42"),
                startTidspunkt = tidspunktSomHarPassert
            )
        )
        repo.oppdaterModellEtterHendelse(
            oppdatert.copy(
                notifikasjonId = uuid("42"),
                tilstand = AVHOLDT
            )
        )
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        assertEquals(emptyList(), hendelseProdusent.hendelser)
    }


    @Test
    fun `oppdater etter avholdt fører til ny skedulert avholdt`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        /**
         * siden tilstand kan settes via API og vi automatisk setter til avholdt
         * så bør vi passe på at oppgaver som gjenåpnes fortsatt settes til avholdt
         * tenkt scenario er at lagg i en produsent fører til at vi får en oppdatering på tilstand etter at vi har satt
         * avholdt. da bør kalenderavtalen forbli avholdt og ikke ende i en åpen state
         */

        val (repo, hendelseProdusent, service) = setupTestApp(database)

        repo.oppdaterModellEtterHendelse(opprettet.copy(startTidspunkt = tidspunktSomHarPassert))
        repo.oppdaterModellEtterHendelse(oppdatert.copy(tilstand = AVLYST))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)
        assertEquals(emptyList(), hendelseProdusent.hendelser)

        repo.oppdaterModellEtterHendelse(oppdatert.copy(tilstand = ARBEIDSGIVER_HAR_GODTATT))
        service.settKalenderavtalerAvholdtBasertPåTidspunkt(now = localDateTimeNow)

        hendelseProdusent.hendelser.first().also {
            it as HendelseModel.KalenderavtaleOppdatert
            assertEquals(AVHOLDT, it.tilstand)
        }
    }

}

private fun setupTestApp(database: Database): Triple<SkedulertUtgåttRepository, FakeHendelseProdusent, SkedulertUtgåttService> {
    val repo = SkedulertUtgåttRepository(database)
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertUtgåttService(repo, hendelseProdusent)
    return Triple(repo, hendelseProdusent, service)
}