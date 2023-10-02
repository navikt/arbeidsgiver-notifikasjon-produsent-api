package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.*
import java.time.LocalTime.MIDNIGHT
import java.time.LocalTime.NOON
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
            opprettetTidspunkt = oppgaveOpprettetTidspunkt,
            frist = førsteFrist,
        ),
        eksterneVarsler = listOf(),
    ),
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
    påminnelse = HendelseModel.Påminnelse(
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateEtterOpprettelse(
            etterOpprettelse = ISO8601Period.parse("P7D"),
            opprettetTidspunkt = fristEndretTidspunkt,
            frist = andreFrist,
        ),
        eksterneVarsler = listOf(),
    ),
)

class SkedulertPåminnelseUtsattFristPåUtførtTests : DescribeSpec({
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertPåminnelseService(hendelseProdusent)

    describe("Ingen påminnelse når oppgave er utført") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(oppgaveUtført)
        service.processHendelse(fristUtsatt)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("Ingen påminnelse hvis utført før påminnelse for ny frist") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt)
        service.processHendelse(oppgaveUtført)

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(5), NOON).inOsloAsInstant())
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }
})
















