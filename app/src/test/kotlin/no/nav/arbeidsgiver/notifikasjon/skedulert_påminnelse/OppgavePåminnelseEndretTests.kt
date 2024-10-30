package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.flow.merge
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime.MIDNIGHT
import java.time.LocalTime.NOON
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.util.*

class OppgavePåminnelseEndretTests : DescribeSpec({
    val metadata = PartitionHendelseMetadata(0, 0)
    describe("Påminnelse blir opprettet når oppgave uten påminnelse får lagt til påminnelse ") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(hendelseProdusent)
        service.processHendelse(oppgaveUtenPaminnelseOpprettet, metadata)
        service.processHendelse(
            HendelseModel.OppgavePåminnelseEndret(
                virksomhetsnummer = oppgaveUtenPaminnelseOpprettet.virksomhetsnummer,
                hendelseId = UUID.randomUUID(),
                produsentId = oppgaveUtenPaminnelseOpprettet.produsentId,
                kildeAppNavn = oppgaveUtenPaminnelseOpprettet.kildeAppNavn,
                notifikasjonId = oppgaveUtenPaminnelseOpprettet.notifikasjonId,
                frist = oppgaveUtenPaminnelseOpprettet.frist,
                oppgaveOpprettetTidspunkt = oppgaveUtenPaminnelseOpprettet.opprettetTidspunkt.toInstant(),
                påminnelse = førstePåminnelse,
                merkelapp = "merkelapp",
                idempotenceKey = null
            ), metadata
        )

        it("Sender påminnelse opprettet") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(1), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 1
        }
    }

    describe("Påminnelse blir ikke opprettet når oppgave med påminnelse får påminnelse fjernet") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(hendelseProdusent)
        service.processHendelse(oppgaveMedPaminnelseOpprettet, metadata)
        service.processHendelse(
            HendelseModel.OppgavePåminnelseEndret(
                virksomhetsnummer = oppgaveUtenPaminnelseOpprettet.virksomhetsnummer,
                hendelseId = UUID.randomUUID(),
                produsentId = oppgaveUtenPaminnelseOpprettet.produsentId,
                kildeAppNavn = oppgaveUtenPaminnelseOpprettet.kildeAppNavn,
                notifikasjonId = oppgaveUtenPaminnelseOpprettet.notifikasjonId,
                frist = oppgaveUtenPaminnelseOpprettet.frist,
                oppgaveOpprettetTidspunkt = oppgaveUtenPaminnelseOpprettet.opprettetTidspunkt.toInstant(),
                påminnelse = null,
                merkelapp = "merkelapp",
                idempotenceKey = null
            ), metadata
        )

        it("Sender ikke påminnelse opprettet") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(1), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Påminnelse blir opprettet når oppgave med påminneølse får påminnelse sin endret") {
        val hendelseProdusent = FakeHendelseProdusent()
        val service = SkedulertPåminnelseService(hendelseProdusent)
        service.processHendelse(oppgaveMedPaminnelseOpprettet, metadata)
        service.processHendelse(
            HendelseModel.OppgavePåminnelseEndret(
                virksomhetsnummer = oppgaveUtenPaminnelseOpprettet.virksomhetsnummer,
                hendelseId = UUID.randomUUID(),
                produsentId = oppgaveUtenPaminnelseOpprettet.produsentId,
                kildeAppNavn = oppgaveUtenPaminnelseOpprettet.kildeAppNavn,
                notifikasjonId = oppgaveUtenPaminnelseOpprettet.notifikasjonId,
                frist = oppgaveUtenPaminnelseOpprettet.frist,
                oppgaveOpprettetTidspunkt = oppgaveUtenPaminnelseOpprettet.opprettetTidspunkt.toInstant(),
                påminnelse = andrePåminnelse,
                merkelapp = "merkelapp",
                idempotenceKey = null
            ), metadata
        )

        it("Sender ikke påminnelse opprettet før ny påmminelse skal opprettes") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(1), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(2), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 1
        }
    }
})


private val dayZero = LocalDate.parse("2020-01-01")
private val frist = dayZero.plusWeeks(2)

private val oppgaveOpprettetTidspunkt = OffsetDateTime.of(dayZero, MIDNIGHT, UTC)

private val oppgaveUtenPaminnelseOpprettet = HendelseModel.OppgaveOpprettet(
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
    frist = frist,
    påminnelse = null,
    sakId = null,
)

private val førstePåminnelse = HendelseModel.Påminnelse(
    tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateEtterOpprettelse(
        etterOpprettelse = ISO8601Period.parse("P4D"),
        opprettetTidspunkt = oppgaveUtenPaminnelseOpprettet.opprettetTidspunkt,
        frist = frist,
        startTidspunkt = null,
    ),
    eksterneVarsler = listOf(),
)

private val andrePåminnelse = HendelseModel.Påminnelse(
    tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateEtterOpprettelse(
        etterOpprettelse = ISO8601Period.parse("P11D"),
        opprettetTidspunkt = oppgaveUtenPaminnelseOpprettet.opprettetTidspunkt,
        frist = frist,
        startTidspunkt = null,
    ),
    eksterneVarsler = listOf(),
)

private val oppgaveMedPaminnelseOpprettet = HendelseModel.OppgaveOpprettet(
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
    frist = frist,
    påminnelse = førstePåminnelse,
    sakId = null,
)

