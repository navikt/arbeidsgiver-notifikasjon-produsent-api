package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

class SkedulertPåminnelseServiceTests : DescribeSpec({
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertPåminnelseService(hendelseProdusent)
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
        opprettetTidspunkt = OffsetDateTime.now().minusDays(14),
        kildeAppNavn = "",
        produsentId = "",
        grupperingsid = null,
        eksterneVarsler = listOf(),
        hardDelete = null,
        frist = null,
        påminnelse = null,
    )
    val tidspunktSomHarPassert = LocalDate.now().minusDays(1).atTime(LocalTime.MAX)
    val tidspunktSomIkkeHarPassert = LocalDate.now().plusDays(2).atTime(LocalTime.MAX)

    describe("Skedulerer påminnelse når påminnelsestidspunkt har passert") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet.medPåminnelse(tidspunktSomHarPassert))
        service.sendAktuellePåminnelser()

        hendelseProdusent.hendelser.first() should beInstanceOf<HendelseModel.PåminnelseOpprettet>()
    }
    describe("Skedulerer utgått når påminnelsestidspunkt har passert og det finnes en på kø som ikke har passert") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet.medPåminnelse(tidspunktSomIkkeHarPassert, uuid("11")))
        service.processHendelse(oppgaveOpprettet.medPåminnelse(tidspunktSomHarPassert, uuid("22")))
        service.sendAktuellePåminnelser()

        hendelseProdusent.hendelser shouldHaveSize 1
        hendelseProdusent.hendelser.first() should beInstanceOf<HendelseModel.PåminnelseOpprettet>()
        hendelseProdusent.hendelser.first().aggregateId shouldBe uuid("22")
    }

    describe("noop når påminnelsestidspunkt ikke har passert enda") {
        hendelseProdusent.clear()
        service.processHendelse(
            oppgaveOpprettet.medPåminnelse(tidspunktSomIkkeHarPassert)
        )
        service.sendAktuellePåminnelser()

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
            hendelseProdusent.clear()
            service.processHendelse(
                oppgaveOpprettet.medPåminnelse(tidspunktSomHarPassert)
            )
            service.processHendelse(hendelse)
            service.sendAktuellePåminnelser()

            hendelseProdusent.hendelser shouldBe emptyList()
        }
    }

})

private fun HendelseModel.OppgaveOpprettet.medPåminnelse(
    tidspunkt: LocalDateTime,
    uuid: UUID = notifikasjonId
) = copy(
    notifikasjonId = uuid,
    påminnelse = HendelseModel.Påminnelse(
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
            tidspunkt,
            opprettetTidspunkt,
            frist
        ),
        eksterneVarsler = listOf()
    ),
)
