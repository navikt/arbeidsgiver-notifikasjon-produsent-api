package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDate
import java.time.OffsetDateTime

class OppgaveUtgåttTests : DescribeSpec({
    val oppgaveOpprettet = HendelseModel.OppgaveOpprettet(
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
    val fristSomHarPassert = LocalDate.now().minusDays(1)
    val fristSomIkkeHarPassert = LocalDate.now().plusDays(2)

    describe("Skedulerer utgått når frist har passert") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(frist = fristSomHarPassert))
        service.settOppgaverUtgåttBasertPåFrist()

        hendelseProdusent.hendelser.first() should beInstanceOf<HendelseModel.OppgaveUtgått>()
    }

    describe("Skedulerer utgått når frist har passert og det finnes en frist på kø som ikke har passert") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(notifikasjonId = uuid("11"), frist = fristSomIkkeHarPassert))
        repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(notifikasjonId = uuid("22"), frist = fristSomHarPassert))
        service.settOppgaverUtgåttBasertPåFrist()

        hendelseProdusent.hendelser shouldHaveSize 1
        hendelseProdusent.hendelser.first() should beInstanceOf<HendelseModel.OppgaveUtgått>()
        hendelseProdusent.hendelser.first().aggregateId shouldBe uuid("22")
    }

    describe("noop når frist ikke har passert enda") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(frist = fristSomIkkeHarPassert))
        service.settOppgaverUtgåttBasertPåFrist()

        hendelseProdusent.hendelser shouldBe emptyList()
    }

    describe("noop når aggregat er fjernet") {
        withData(listOf(
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
        )) { hendelse ->
            val (repo, hendelseProdusent, service) = setupTestApp()

            repo.oppdaterModellEtterHendelse(oppgaveOpprettet.copy(frist = fristSomHarPassert))
            repo.oppdaterModellEtterHendelse(hendelse)

            service.settOppgaverUtgåttBasertPåFrist()

            hendelseProdusent.hendelser shouldBe emptyList()
        }
    }

})

private fun DescribeSpec.setupTestApp(): Triple<SkedulertUtgåttRepository, FakeHendelseProdusent, SkedulertUtgåttService> {
    val database = testDatabase(SkedulertUtgått.databaseConfig)
    val repo = SkedulertUtgåttRepository(database)
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertUtgåttService(repo, hendelseProdusent)
    return Triple(repo, hendelseProdusent, service)
}
