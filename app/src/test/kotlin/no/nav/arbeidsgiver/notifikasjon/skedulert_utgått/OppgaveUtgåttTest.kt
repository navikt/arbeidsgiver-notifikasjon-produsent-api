package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class OppgaveUtgåttTest {
    private val oppgaveOpprettet = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        merkelapp = "123",
        eksternId = "42",
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        hendelseId = uuid("1"),
        notifikasjonId = uuid("1"),
        tekst = "test",
        lenke = "https://nav.no",
        opprettetTidspunkt = OffsetDateTime.now(),
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = null,
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )
    private val fristSomHarPassert = LocalDate.now().minusDays(1)
    private val fristSomIkkeHarPassert = LocalDate.now().plusDays(2)

    @Test
    fun `Skedulerer utgått når frist har passert`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val (repo, hendelseProdusent, service) = setupTestApp(database)

        repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(frist = fristSomHarPassert))
        service.settOppgaverUtgåttBasertPåFrist()

        hendelseProdusent.hendelser.first() as HendelseModel.OppgaveUtgått
    }

    @Test
    fun `Skedulerer utgått når frist har passert og det finnes en frist på kø som ikke har passert`() =
        withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
            val (repo, hendelseProdusent, service) = setupTestApp(database)

            repo.oppdaterModellEtterHendelse(
                oppgaveOpprettet.copy(
                    notifikasjonId = uuid("11"),
                    frist = fristSomIkkeHarPassert
                )
            )
            repo.oppdaterModellEtterHendelse(
                oppgaveOpprettet.copy(
                    notifikasjonId = uuid("22"),
                    frist = fristSomHarPassert
                )
            )
            service.settOppgaverUtgåttBasertPåFrist()

            assertEquals(1, hendelseProdusent.hendelser.size)
            hendelseProdusent.hendelser.first() as HendelseModel.OppgaveUtgått
            assertEquals(uuid("22"), hendelseProdusent.hendelser.first().aggregateId)
        }

    @Test
    fun `noop når frist ikke har passert enda`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        val (repo, hendelseProdusent, service) = setupTestApp(database)

        repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(frist = fristSomIkkeHarPassert))
        service.settOppgaverUtgåttBasertPåFrist()

        assertEquals(emptyList(), hendelseProdusent.hendelser)
    }

    @Test
    fun `noop når aggregat er fjernet`() = withTestDatabase(SkedulertUtgått.databaseConfig) { database ->
        listOf(
            HendelseModel.HardDelete(
                aggregateId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                deletedAt = OffsetDateTime.now(),
                grupperingsid = null,
                merkelapp = oppgaveOpprettet.merkelapp,
            ),
            HendelseModel.OppgaveUtført(
                notifikasjonId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                hardDelete = null,
                nyLenke = null,
                utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
            ),
            HendelseModel.OppgaveUtgått(
                notifikasjonId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                hardDelete = null,
                utgaattTidspunkt = OffsetDateTime.now(),
                nyLenke = null,
            )
        ).forEach { hendelse ->
            val (repo, hendelseProdusent, service) = setupTestApp(database)

            repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(frist = fristSomHarPassert))
            repo.oppdaterModellEtterHendelse(hendelse)

            service.settOppgaverUtgåttBasertPåFrist()

            assertEquals(emptyList(), hendelseProdusent.hendelser)
        }
    }

}

private fun setupTestApp(database: Database): Triple<SkedulertUtgåttRepository, FakeHendelseProdusent, SkedulertUtgåttService> {
    val repo = SkedulertUtgåttRepository(database)
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertUtgåttService(repo, hendelseProdusent)
    return Triple(repo, hendelseProdusent, service)
}
