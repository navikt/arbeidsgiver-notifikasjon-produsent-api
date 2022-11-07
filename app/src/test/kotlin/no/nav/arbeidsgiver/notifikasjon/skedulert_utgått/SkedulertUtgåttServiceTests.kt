package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.*
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
    )

    describe("Skedulerer utgått når frist har passert") {
        val fristSomHarPassert = LocalDate.now().minusDays(1)
        hendelseProdusent.clear()
        service.processHendelse(
            oppgaveOpprettet.copy(frist = fristSomHarPassert)
        )
        service.sendVedUtgåttFrist()

        hendelseProdusent.hendelser.first() should beInstanceOf<HendelseModel.OppgaveUtgått>()
    }

    describe("noop når frist ikke har passert enda") {
        val fristSomIkkeHarPassert = LocalDate.now().plusDays(1)
        hendelseProdusent.clear()
        service.processHendelse(
            oppgaveOpprettet.copy(frist = fristSomIkkeHarPassert)
        )
        service.sendVedUtgåttFrist()

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
            ),
            HendelseModel.OppgaveUtført(
                notifikasjonId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                hardDelete = null,
            ),
            HendelseModel.OppgaveUtgått(
                notifikasjonId = oppgaveOpprettet.aggregateId,
                virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
                hendelseId = uuid("2"),
                produsentId = oppgaveOpprettet.virksomhetsnummer,
                kildeAppNavn = oppgaveOpprettet.virksomhetsnummer,
                hardDelete = null,
                utgaattTidspunkt = OffsetDateTime.now()
            )
        )) { hendelse ->
            val fristSomHarPassert = LocalDate.now().minusDays(1)
            hendelseProdusent.clear()
            service.processHendelse(
                oppgaveOpprettet.copy(frist = fristSomHarPassert)
            )
            service.processHendelse(hendelse)
            service.sendVedUtgåttFrist()

            hendelseProdusent.hendelser shouldBe emptyList()
        }
    }

})
