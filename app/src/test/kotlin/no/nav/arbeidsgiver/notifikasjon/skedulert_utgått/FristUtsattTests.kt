package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDate
import java.time.LocalTime.MIDNIGHT
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC


private val dayZero = LocalDate.parse("2020-01-01")
private val opprinneligFrist = dayZero.plusWeeks(2)
private val utsattFrist = dayZero.plusWeeks(3)

private val oppgaveOpprettetTidspunkt = OffsetDateTime.of(dayZero, MIDNIGHT, UTC)
private val oppgaveOpprettetMedFrist = HendelseModel.OppgaveOpprettet(
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
    frist = opprinneligFrist,
    påminnelse = null,
)
private val oppgaveOpprettetUtenFrist = oppgaveOpprettetMedFrist.copy(frist = null)

private val fristEndretTidspunkt = oppgaveOpprettetTidspunkt.plusWeeks(1)
private val fristUtsatt = HendelseModel.FristUtsatt(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    hendelseId = uuid("3"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    notifikasjonId = oppgaveOpprettetMedFrist.notifikasjonId,
    fristEndretTidspunkt = fristEndretTidspunkt.toInstant(),
    frist = utsattFrist,
    påminnelse = null,
)

private val oppgaveUtgått = OppgaveUtgått(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    notifikasjonId = oppgaveOpprettetMedFrist.notifikasjonId,
    hendelseId = uuid("4"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    hardDelete = null,
    utgaattTidspunkt = opprinneligFrist.atTime(MIDNIGHT).atOffset(UTC),
    nyLenke = null,
)
private val oppgaveUtført = HendelseModel.OppgaveUtført(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    notifikasjonId = oppgaveOpprettetMedFrist.notifikasjonId,
    hendelseId = uuid("5"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    hardDelete = null,
    utfoertTidspunkt = opprinneligFrist.atTime(MIDNIGHT).atOffset(UTC),
    nyLenke = null,
)
private val softDelete = HendelseModel.SoftDelete(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    aggregateId = oppgaveOpprettetMedFrist.aggregateId,
    hendelseId = uuid("6"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    deletedAt = opprinneligFrist.atTime(MIDNIGHT).atOffset(UTC),
)
private val hardDelete = HendelseModel.HardDelete(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    aggregateId = oppgaveOpprettetMedFrist.aggregateId,
    hendelseId = uuid("7"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    deletedAt = opprinneligFrist.atTime(MIDNIGHT).atOffset(UTC),
)


class FristUtsattTests: DescribeSpec({
    describe("Frist utsatt på oppgave uten frist") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetUtenFrist)
        service.processHendelse(fristUtsatt)

        it("Sender ett oppgaveUtgått-event basert på utsatt frist") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().let {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt på oppgave med eksisterende frist") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(fristUtsatt)

        it("Sender ett oppgaveUtgått-event basert på utsatt frist") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().let {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt parallelt med oppgaveUtgått, hvor utsettelse kommer først") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(fristUtsatt)
        service.processHendelse(oppgaveUtgått)
        it("Sender ett oppgaveUtgått-event for utsatt frist") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().let {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt parallelt med oppgaveUtgått, hvor utgått kommer først") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(oppgaveUtgått)
        service.processHendelse(fristUtsatt)
        it("Sender ett oppgaveUtgått-event for utsatt frist") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().let {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt parallelt med oppgave utført, hvor utsettelse kommer først") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(fristUtsatt)
        service.processHendelse(oppgaveUtført)
        it("Skal ikke sende noen event, siden oppgaven er utført") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Frist utsatt parallelt med oppgave utført, hvor utført kommer først") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(fristUtsatt)
        service.processHendelse(oppgaveUtført)
        it("Skal ikke sende noen event, siden oppgaven er utført") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Softdelete på en oppgave med utsatt frist, soft-delete først") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(softDelete)
        service.processHendelse(fristUtsatt)
        it("Skal ikke sende noen event, siden oppgaven er soft-deleted") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }
    describe("Softdelete på en oppgave med utsatt frist, soft-delete sist") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(fristUtsatt)
        service.processHendelse(softDelete)
        it("Skal ikke sende noen event, siden oppgaven er soft-deleted") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Harddelete på en oppgave med utsatt frist, hard-delete først") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(hardDelete)
        service.processHendelse(fristUtsatt)
        it("Skal ikke sende noen event, siden oppgaven er hard-deleted") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Harddelete på en oppgave med utsatt frist, hard-delete sist") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertUtgåttService(hendelseProdusent)
        service.processHendelse(oppgaveOpprettetMedFrist)
        service.processHendelse(fristUtsatt)
        service.processHendelse(hardDelete)
        it("Skal ikke sende noen event, siden oppgaven er hard-deleted") {
            service.sendVedUtgåttFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }
})