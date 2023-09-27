package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDateTime
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.*
import java.time.LocalTime.MIDNIGHT
import java.time.LocalTime.NOON
import java.time.ZoneOffset.UTC
import java.util.*

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
private val andreFrist = dayZero.plusWeeks(3)

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

private val påminnelseOpprettet1 = HendelseModel.PåminnelseOpprettet(
    hendelseId = uuid("2"),
    virksomhetsnummer = oppgaveOpprettet.virksomhetsnummer,
    produsentId = oppgaveOpprettet.produsentId,
    kildeAppNavn = oppgaveOpprettet.kildeAppNavn,
    notifikasjonId = oppgaveOpprettet.notifikasjonId,
    bestillingHendelseId = oppgaveOpprettet.hendelseId,
    opprettetTidpunkt = oppgaveOpprettet.påminnelse!!.tidspunkt.påminnelseTidspunkt,
    fristOpprettetTidspunkt = oppgaveOpprettet.opprettetTidspunkt.toInstant(),
    frist = oppgaveOpprettet.frist,
    tidspunkt = oppgaveOpprettet.påminnelse!!.tidspunkt,
    eksterneVarsler = oppgaveOpprettet.påminnelse!!.eksterneVarsler,
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

class SkedulertPåminnelseRaceConditionTests: DescribeSpec({
    val hendelseProdusent = FakeHendelseProdusent()
    val service = SkedulertPåminnelseService(hendelseProdusent)

    describe("Hendelsene kommer i 'riktig' rekkefølge") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(påminnelseOpprettet1)
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

    describe("Hendelsene kommer i 'feil' rekkefølge") {
        hendelseProdusent.clear()
        service.processHendelse(oppgaveOpprettet)
        service.processHendelse(fristUtsatt)
        service.processHendelse(påminnelseOpprettet1)
        service.sendAktuellePåminnelser(now = LocalDateTime.of(dayZero.plusWeeks(2), NOON).inOsloAsInstant())

        it("Sender skjedulert påminnelse") {
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser[0].shouldBeInstanceOf<HendelseModel.PåminnelseOpprettet>().let {
                it.bestillingHendelseId shouldBe fristUtsatt.hendelseId
                it.tidspunkt.påminnelseTidspunkt.asOsloLocalDate() shouldBe dayZero.plusWeeks(2)
            }
        }
    }

})