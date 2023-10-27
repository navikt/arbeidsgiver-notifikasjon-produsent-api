package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDate
import java.time.OffsetDateTime

class SkedulertUtgåttServiceTests : DescribeSpec({
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertUtgåttService(hendelseProdusent)
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
    val now = LocalDate.parse("2020-01-01")
    val fristSomHarPassert = now.minusDays(1)
    val fristSomIkkeHarPassert = now.plusDays(2)

    describe("Skedulerer utgått når frist har passert") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet.copy(frist = fristSomHarPassert))
        service.sendVedUtgåttFrist(now = now)

        hendelseProdusent.hendelser.first() should beInstanceOf<HendelseModel.OppgaveUtgått>()
    }
    describe("Skedulerer utgått når frist har passert og det finnes en frist på kø som ikke har passert") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet.copy(notifikasjonId = uuid("11"), frist = fristSomIkkeHarPassert))
        service.processHendelse(oppgaveOpprettet.copy(notifikasjonId = uuid("22"), frist = fristSomHarPassert))
        service.sendVedUtgåttFrist(now = now)

        hendelseProdusent.hendelser shouldHaveSize 1
        hendelseProdusent.hendelser.first() should beInstanceOf<HendelseModel.OppgaveUtgått>()
        hendelseProdusent.hendelser.first().aggregateId shouldBe uuid("22")
    }

    describe("noop når frist ikke har passert enda") {
        hendelseProdusent.clear()
        service.processHendelse(
            oppgaveOpprettet.copy(frist = fristSomIkkeHarPassert)
        )
        service.sendVedUtgåttFrist(now = now)

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
            hendelseProdusent.clear()
            service.processHendelse(
                oppgaveOpprettet.copy(frist = fristSomHarPassert)
            )
            service.processHendelse(hendelse)
            service.sendVedUtgåttFrist(now = now)

            hendelseProdusent.hendelser shouldBe emptyList()
        }
    }

})
