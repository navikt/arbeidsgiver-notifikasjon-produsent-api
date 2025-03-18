package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime.MIDNIGHT
import java.time.LocalTime.NOON
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC

/**
 * Hvis en påminnelse sendes samtidig som fristen utsettes, så kan vi ikke garantere rekkefølgen på
 * de to eventene. Situasjonen kan beskrives slik (hvor `||` betyr parallelt):
 *
 * 1. OppgaveOpprettet
 * 2. (PåminnelseOpprettet (for OppgaveOpprettet) || FristUtsatt )
 *
 * Som kan serialiseres på to måter. Enten:
 *
 * 1. OppgaveOpprettet
 * 2. PåminnelseOpprettet (for OppgaveOpprettet)
 * 3. FristUtsatt
 *
 * eller:
 * 1. OppgaveOpprettet
 * 2. FristUtsatt
 * 3. PåminnelseOpprettet (for OppgaveOpprettet)
 *
 * I begge tilfeller ønsker vi at det senere kommer en PåminnelseOpprettet for FristUtsatt.
 */

class UtsattFristTests : DescribeSpec({
    describe("Ingen påminnelse når oppgave er utført") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(oppgaveUtført)
        service.processHendelse(fristUtsatt)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Ingen påminnelse hvis utført før påminnelse for ny frist") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt)
        service.processHendelse(oppgaveUtført)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Sender påminnelse for utsatt frist, selv om påminnelse for opprinnelig frist er sendt") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(påminnelseOpprettet)
        service.processHendelse(fristUtsatt)

        it("Sender skjedulert påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(2), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<HendelseModel.PåminnelseOpprettet>().let {
                it.bestillingHendelseId shouldBe fristUtsatt.hendelseId
                it.tidspunkt.påminnelseTidspunkt.asOsloLocalDate() shouldBe dayZero.plusWeeks(2)
            }
        }
    }

    describe("Sender påminnelse for utsatt frist, selv om påminnelse for opprinnelig frist kom etter utsettelsen") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt)
        service.processHendelse(påminnelseOpprettet)
        service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(2), NOON).inOsloAsInstant())

        it("Sender skjedulert påminnelse") {
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<HendelseModel.PåminnelseOpprettet>().let {
                it.bestillingHendelseId shouldBe fristUtsatt.hendelseId
                it.tidspunkt.påminnelseTidspunkt.asOsloLocalDate() shouldBe dayZero.plusWeeks(2)
            }
        }
    }

    describe("Når ny frist settes (selv uten påminnelse), fjernes eksisterende skedulert påminnelse") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt.copy(påminnelse = null))

        it("Sender kun påminnelse for utsatt frist") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Når ny frist settes med ny påminnelse, fjernes eksisterende skedulert påminnelse") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<HendelseModel.PåminnelseOpprettet>().let {
                it.bestillingHendelseId shouldBe fristUtsatt.hendelseId
                it.frist shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Sender ikke påminnelse hvis oppgave blir soft-deleted") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt)
        service.processHendelse(softDelete)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Sender ikke påminnelse hvis oppgave er soft-deleted") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(softDelete)
        service.processHendelse(fristUtsatt)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Sender ikke påminnelse hvis oppgave blir hard-deleted") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt)
        service.processHendelse(hardDelete)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Sender ikke påminnelse hvis oppgave er hard-deleted") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(hardDelete)
        service.processHendelse(fristUtsatt)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }
})

private val dayZero = LocalDate.parse("2020-01-01")
private val førsteFrist = dayZero.plusWeeks(2)
private val andreFrist = dayZero.plusWeeks(4)

private val oppgaveOpprettetTidspunkt = OffsetDateTime.of(dayZero, MIDNIGHT, UTC)

private val oppgaveOpprettet = HendelseModel.OppgaveOpprettet(
    virksomhetsnummer = "1".repeat(9),
    notifikasjonId = uuid("1"),
    hendelseId = uuid("1"),
    produsentId = "eksempel-produsent-id",
    kildeAppNavn = "eksempel-kiilde-app-navn",
    merkelapp = "eksempel-merkelapp",
    eksternId = "eksempel-ekstern-id",
    mottakere = listOf(
        HendelseModel.AltinnMottaker(
            virksomhetsnummer = "1".repeat(9),
            serviceCode = "1",
            serviceEdition = "1",
        )
    ),
    tekst = "eksemple-tekst",
    grupperingsid = null,
    lenke = "https://nav.no",
    opprettetTidspunkt = oppgaveOpprettetTidspunkt,
    eksterneVarsler = listOf(),
    hardDelete = null,
    frist = førsteFrist,
    påminnelse = HendelseModel.Påminnelse(
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateEtterOpprettelse(
            etterOpprettelse = ISO8601Period.parse("P7D"),
            notifikasjonOpprettetTidspunkt = oppgaveOpprettetTidspunkt,
            frist = førsteFrist,
            startTidspunkt = null,
        ),
        eksterneVarsler = listOf(),
    ),
    sakId = null,
)


private val oppgaveUtført = HendelseModel.OppgaveUtført(
    virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
    hendelseId = uuid("2"),
    produsentId = oppgaveOpprettet.produsentId,
    kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
    notifikasjonId = oppgaveOpprettet.notifikasjonId,
    utfoertTidspunkt = oppgaveOpprettet.opprettetTidspunkt.plusWeeks(1),
    hardDelete = null,
    nyLenke = null,
)

private val fristEndretTidspunkt = oppgaveOpprettet.opprettetTidspunkt.plusWeeks(1)

private val fristUtsatt = HendelseModel.FristUtsatt(
    virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
    hendelseId = uuid("3"),
    produsentId = oppgaveOpprettet.produsentId,
    kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
    notifikasjonId = oppgaveOpprettet.notifikasjonId,
    fristEndretTidspunkt = fristEndretTidspunkt.toInstant(),
    frist = andreFrist,
    merkelapp = oppgaveOpprettet.merkelapp,
    påminnelse = HendelseModel.Påminnelse(
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateEtterOpprettelse(
            etterOpprettelse = ISO8601Period.parse("P7D"),
            notifikasjonOpprettetTidspunkt = fristEndretTidspunkt,
            frist = andreFrist,
            startTidspunkt = null,
        ),
        eksterneVarsler = listOf(),
    ),
)

private val påminnelseOpprettet = HendelseModel.PåminnelseOpprettet(
    hendelseId = uuid("4"),
    virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
    produsentId = oppgaveOpprettet.produsentId,
    kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
    notifikasjonId = oppgaveOpprettet.notifikasjonId,
    bestillingHendelseId = oppgaveOpprettet.hendelseId,
    opprettetTidpunkt = oppgaveOpprettet.påminnelse!!.tidspunkt.påminnelseTidspunkt,
    fristOpprettetTidspunkt = oppgaveOpprettet.opprettetTidspunkt.toInstant(),
    frist = oppgaveOpprettet.frist,
    tidspunkt = oppgaveOpprettet.påminnelse.tidspunkt,
    eksterneVarsler = oppgaveOpprettet.påminnelse.eksterneVarsler,
)

private val softDelete = HendelseModel.SoftDelete(
    hendelseId = uuid("5"),
    virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
    produsentId = oppgaveOpprettet.produsentId,
    kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
    aggregateId = oppgaveOpprettet.notifikasjonId,
    deletedAt = OffsetDateTime.now(),
    grupperingsid = null,
    merkelapp = oppgaveOpprettet.merkelapp,
)

private val hardDelete = HendelseModel.HardDelete(
    hendelseId = uuid("6"),
    virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
    produsentId = oppgaveOpprettet.produsentId,
    kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
    aggregateId = oppgaveOpprettet.notifikasjonId,
    deletedAt = OffsetDateTime.now(),
    grupperingsid = null,
    merkelapp = oppgaveOpprettet.merkelapp,
)
