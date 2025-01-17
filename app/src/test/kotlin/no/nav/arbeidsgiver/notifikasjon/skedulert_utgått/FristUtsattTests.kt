package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDate
import java.time.LocalTime.MIDNIGHT
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.util.*


private val dayZero = LocalDate.parse("2020-01-01")
private val opprinneligFrist = dayZero.plusWeeks(2)
private val utsattFrist = dayZero.plusWeeks(3)

private val sakId = UUID.randomUUID()

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
    grupperingsid = "en-grupperings-id",
    lenke = "https://nav.no",
    opprettetTidspunkt = oppgaveOpprettetTidspunkt,
    eksterneVarsler = listOf(),
    hardDelete = null,
    frist = opprinneligFrist,
    påminnelse = null,
    sakId = sakId,
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
    merkelapp = "merkelapp"
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
    grupperingsid = null,
    merkelapp = oppgaveOpprettetMedFrist.merkelapp,
)
private val hardDelete = HendelseModel.HardDelete(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    aggregateId = oppgaveOpprettetMedFrist.aggregateId,
    hendelseId = uuid("7"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    deletedAt = opprinneligFrist.atTime(MIDNIGHT).atOffset(UTC),
    grupperingsid = null,
    merkelapp = oppgaveOpprettetMedFrist.merkelapp,
)
private val hardDeleteSak = HendelseModel.HardDelete(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    aggregateId = sakId,
    hendelseId = uuid("8"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    deletedAt = opprinneligFrist.atTime(MIDNIGHT).atOffset(UTC),
    grupperingsid = oppgaveOpprettetMedFrist.grupperingsid,
    merkelapp = oppgaveOpprettetMedFrist.merkelapp,
)
private val softDeleteSak = HendelseModel.SoftDelete(
    virksomhetsnummer = oppgaveOpprettetMedFrist.virksomhetsnummer,
    aggregateId = sakId,
    hendelseId = uuid("9"),
    produsentId = oppgaveOpprettetMedFrist.produsentId,
    kildeAppNavn = oppgaveOpprettetMedFrist.kildeAppNavn,
    deletedAt = opprinneligFrist.atTime(MIDNIGHT).atOffset(UTC),
    grupperingsid = oppgaveOpprettetMedFrist.grupperingsid,
    merkelapp = oppgaveOpprettetMedFrist.merkelapp,
)


class FristUtsattTests: DescribeSpec({
    describe("Frist utsatt på oppgave uten frist") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetUtenFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)

        it("Sender ett oppgaveUtgått-event basert på utsatt frist") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().also {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt på oppgave med eksisterende frist") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)

        it("Sender ett oppgaveUtgått-event basert på utsatt frist") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().also {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt parallelt med oppgaveUtgått, hvor utsettelse kommer først") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)
        repo.oppdaterModellEtterHendelse(oppgaveUtgått)

        it("Sender ett oppgaveUtgått-event for utsatt frist") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().also {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt parallelt med oppgaveUtgått, hvor utgått kommer først") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(oppgaveUtgått)
        repo.oppdaterModellEtterHendelse(fristUtsatt)

        it("Sender ett oppgaveUtgått-event for utsatt frist") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<OppgaveUtgått>().also {
                it.utgaattTidspunkt.asOsloLocalDate() shouldBe fristUtsatt.frist
            }
        }
    }

    describe("Frist utsatt parallelt med oppgave utført, hvor utsettelse kommer først") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)
        repo.oppdaterModellEtterHendelse(oppgaveUtført)

        it("Skal ikke sende noen event, siden oppgaven er utført") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Frist utsatt parallelt med oppgave utført, hvor utført kommer først") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)
        repo.oppdaterModellEtterHendelse(oppgaveUtført)

        it("Skal ikke sende noen event, siden oppgaven er utført") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Softdelete på en oppgave med utsatt frist, soft-delete først") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(softDelete)
        repo.oppdaterModellEtterHendelse(fristUtsatt)

        it("Skal ikke sende noen event, siden oppgaven er soft-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Softdelete på en oppgave med utsatt frist, soft-delete sist") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)
        repo.oppdaterModellEtterHendelse(softDelete)

        it("Skal ikke sende noen event, siden oppgaven er soft-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Harddelete på en oppgave med utsatt frist, hard-delete først") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(hardDelete)
        repo.oppdaterModellEtterHendelse(fristUtsatt)

        it("Skal ikke sende noen event, siden oppgaven er hard-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Harddelete på en oppgave med utsatt frist, hard-delete sist") {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)
        repo.oppdaterModellEtterHendelse(hardDelete)

        it("Skal ikke sende noen event, siden oppgaven er hard-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Harddelete på grupperingsid")  {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)
        repo.oppdaterModellEtterHendelse(hardDeleteSak)

        it("Skal ikke sende noen event, siden saken er hard-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Harddelete på grupperingsid, mottatt før saken er opprettet")  {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(hardDeleteSak)
        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)

        it("Skal ikke sende noen event, siden saken er hard-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Softdelete på grupperingsid")  {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)
        repo.oppdaterModellEtterHendelse(softDeleteSak)

        it("Skal ikke sende noen event, siden saken er soft-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Softdelete på grupperingsid, mottatt før saken er opprettet")  {
        val (repo, hendelseProdusent, service) = setupTestApp()

        repo.oppdaterModellEtterHendelse(softDeleteSak)
        repo.oppdaterModellEtterHendelse(oppgaveOpprettetMedFrist)
        repo.oppdaterModellEtterHendelse(fristUtsatt)

        it("Skal ikke sende noen event, siden saken er soft-deleted") {
            service.settOppgaverUtgåttBasertPåFrist(now = utsattFrist.plusDays(1))
            hendelseProdusent.hendelser shouldHaveSize 0
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