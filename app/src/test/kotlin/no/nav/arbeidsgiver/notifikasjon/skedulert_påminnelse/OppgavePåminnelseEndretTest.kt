package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime.MIDNIGHT
import java.time.LocalTime.NOON
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class OppgavePåminnelseEndretTest {
    @Test
    fun `Påminnelse blir opprettet når oppgave uten påminnelse får lagt til påminnelse `() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )
            service.processHendelse(oppgaveUtenPaminnelseOpprettet)
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
                )
            )

            // Sender påminnelse opprettet
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(1), NOON).inOsloAsInstant())
            assertEquals(1, hendelseProdusent.hendelser.size)
        }

    @Test
    fun `Påminnelse blir ikke opprettet når oppgave med påminnelse får påminnelse fjernet`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )
            service.processHendelse(oppgaveMedPaminnelseOpprettet)
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
                )
            )

            // Sender ikke påminnelse opprettet
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(1), NOON).inOsloAsInstant())
            assertEquals(0, hendelseProdusent.hendelser.size)
        }

    @Test
    fun `Påminnelse blir opprettet når oppgave med påminneølse får påminnelse sin endret`() =
        withTestDatabase(SkedulertPåminnelse.databaseConfig) { database ->
            val hendelseProdusent = FakeHendelseProdusent()
            val service = SkedulertPåminnelseService(
                hendelseProdusent = hendelseProdusent,
                database = database
            )
            service.processHendelse(oppgaveMedPaminnelseOpprettet)
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
                )
            )

            // Sender ikke påminnelse opprettet før ny påmminelse skal opprettes
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(1), NOON).inOsloAsInstant())
            assertEquals(0, hendelseProdusent.hendelser.size)
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(2), NOON).inOsloAsInstant())
            assertEquals(1, hendelseProdusent.hendelser.size)
        }
}


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
        notifikasjonOpprettetTidspunkt = oppgaveUtenPaminnelseOpprettet.opprettetTidspunkt,
        frist = frist,
        startTidspunkt = null,
    ),
    eksterneVarsler = listOf(),
)

private val andrePåminnelse = HendelseModel.Påminnelse(
    tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateEtterOpprettelse(
        etterOpprettelse = ISO8601Period.parse("P11D"),
        notifikasjonOpprettetTidspunkt = oppgaveUtenPaminnelseOpprettet.opprettetTidspunkt,
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

