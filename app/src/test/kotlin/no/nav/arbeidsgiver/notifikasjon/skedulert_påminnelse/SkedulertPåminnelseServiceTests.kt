package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

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
        sakId = null,
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
                grupperingsid = null,
                merkelapp = null,
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
                oppgaveOpprettet.medPåminnelse(tidspunktSomHarPassert)
            )
            service.processHendelse(hendelse)
            service.sendAktuellePåminnelser()

            hendelseProdusent.hendelser shouldBe emptyList()
        }
    }

    describe("Skedulerer utgått for alle som har passert innenfor samme time, men ikke de som ikke har passert") {
        hendelseProdusent.clear()
        val now = oppgaveOpprettet.opprettetTidspunkt.plusDays(14).toLocalDateTime()
        val passert1 = now.minus(1, ChronoUnit.HOURS) // key: 11:00
        val passert2 = now.minus(10, ChronoUnit.MINUTES) // key: 12:00
        val passert3 = now.minus(1, ChronoUnit.MINUTES) // key: 12:00
        val ikkePassert1 = now.plus(10, ChronoUnit.MINUTES) // key: 12:00

        service.processHendelse(oppgaveOpprettet.medPåminnelse(passert1, uuid("1")))
        service.processHendelse(oppgaveOpprettet.medPåminnelse(passert2, uuid("2")))
        service.processHendelse(oppgaveOpprettet.medPåminnelse(passert3, uuid("3")))
        service.processHendelse(oppgaveOpprettet.medPåminnelse(ikkePassert1, uuid("4")))

        hendelseProdusent.clear()
        service.sendAktuellePåminnelser(now.inOsloAsInstant())
        hendelseProdusent.hendelser shouldHaveSize 3
        hendelseProdusent.hendelser.map {
            it.aggregateId
        } shouldContainExactlyInAnyOrder  listOf(uuid("1"), uuid("2"), uuid("3"))

        hendelseProdusent.clear()
        service.sendAktuellePåminnelser(ikkePassert1.plusMinutes(1).inOsloAsInstant())
        hendelseProdusent.hendelser shouldHaveSize 1
        hendelseProdusent.hendelser.first().aggregateId shouldBe uuid("4")
    }

}) {
    override fun isolationMode() = IsolationMode.InstancePerTest
}

private fun HendelseModel.OppgaveOpprettet.medPåminnelse(
    tidspunkt: LocalDateTime,
    uuid: UUID = notifikasjonId
) = copy(
    notifikasjonId = uuid,
    hendelseId = uuid,
    påminnelse = HendelseModel.Påminnelse(
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
            tidspunkt,
            opprettetTidspunkt,
            frist
        ),
        eksterneVarsler = listOf()
    ),
)
