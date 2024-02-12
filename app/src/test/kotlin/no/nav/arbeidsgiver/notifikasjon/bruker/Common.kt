package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDeleteUpdate
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Påminnelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.random.Random

suspend fun BrukerRepository.oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse){
    oppdaterModellEtterHendelse(hendelse, HendelseModel.HendelseMetadata(Instant.now()))
}

const val TEST_FNR_1 = "00000000000"

const val TEST_VIRKSOMHET_1 = "000000000"
const val TEST_SERVICE_CODE_1 = "1234"
const val TEST_SERVICE_EDITION_1 = "1"

const val TEST_VIRKSOMHET_2 = "111111111"
const val TEST_SERVICE_CODE_2 = "5678"
const val TEST_SERVICE_EDITION_2 = "2"

val TEST_MOTTAKER_1 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = TEST_VIRKSOMHET_1,
    serviceCode = TEST_SERVICE_CODE_1,
    serviceEdition = TEST_SERVICE_EDITION_1,
)

val TEST_MOTTAKER_2 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = TEST_VIRKSOMHET_2,
    serviceCode = TEST_SERVICE_CODE_2,
    serviceEdition = TEST_SERVICE_EDITION_2,
)

val TEST_TILGANG_1 = BrukerModel.Tilgang.Altinn(
    virksomhet = TEST_VIRKSOMHET_1 ,
    servicecode = TEST_SERVICE_CODE_1,
    serviceedition = TEST_SERVICE_EDITION_1,
)

val TEST_OPPRETTET_TIDSPUNKT_1 = OffsetDateTime.parse("2020-01-01T01:01:01+01")

/* Use randomness to prevent implicit dependencies in unit tests on default values.
 * If it is important that fields match for a certain behaviour, the unit-test should set them
 * explicitly. */
private fun randomLong() = Random.Default.nextLong()
private fun randomMerkelapp() = "merkelapp ${randomLong()}"
private fun randomLenke(tag: String) = "https://example.com/$tag/${randomLong()}"
private fun randomProdusentId() = "produsent-${randomLong()}"
private fun randomKildeAppNavn() = "kilde-app-navn-${randomLong()}"
private fun randomTekst(prefix: String) = "$prefix ${randomLong()}"
private fun randomSakStatus() = SakStatus.values().random()

suspend fun BrukerRepository.beskjedOpprettet(
    notifikasjonId: UUID = UUID.randomUUID(),
    virksomhetsnummer: String = TEST_VIRKSOMHET_1,
    produsentId: String = randomProdusentId(),
    kildeAppNavn: String = randomKildeAppNavn(),
    merkelapp: String = randomMerkelapp(),
    eksternId: String = UUID.randomUUID().toString(),
    mottakere: List<HendelseModel.Mottaker> = listOf(TEST_MOTTAKER_1),
    tekst: String = randomTekst("Beskjed-tekst"),
    grupperingsid: String? = null,
    lenke: String = randomLenke("beskjed"),
    opprettetTidspunkt: OffsetDateTime = TEST_OPPRETTET_TIDSPUNKT_1,
    eksterneVarsler: List<HendelseModel.EksterntVarsel> = listOf(),
    hardDelete: LocalDateTimeOrDuration? = null,
) = HendelseModel.BeskjedOpprettet(
    merkelapp = merkelapp,
    eksternId = eksternId,
    mottakere = mottakere,
    hendelseId = notifikasjonId,
    notifikasjonId = notifikasjonId,
    tekst = tekst,
    grupperingsid = grupperingsid,
    lenke = lenke,
    opprettetTidspunkt = opprettetTidspunkt,
    virksomhetsnummer = virksomhetsnummer,
    kildeAppNavn = kildeAppNavn,
    produsentId = produsentId,
    eksterneVarsler = eksterneVarsler,
    hardDelete = hardDelete,
    sakId = null,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.kalenderavtaleOpprettet(
    notifikasjonId: UUID = UUID.randomUUID(),
    sakId: UUID,
    virksomhetsnummer: String = TEST_VIRKSOMHET_1,
    produsentId: String = randomProdusentId(),
    kildeAppNavn: String = randomKildeAppNavn(),
    merkelapp: String = randomMerkelapp(),
    eksternId: String = UUID.randomUUID().toString(),
    mottakere: List<HendelseModel.Mottaker> = listOf(TEST_MOTTAKER_1),
    tekst: String = randomTekst("kalenderavtale-tekst"),
    grupperingsid: String = randomTekst("kalenderavtale-grupperingsid"),
    lenke: String = randomLenke("kalenderavtale"),
    opprettetTidspunkt: OffsetDateTime = TEST_OPPRETTET_TIDSPUNKT_1,
    tilstand: KalenderavtaleTilstand = KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
    startTidspunkt: OffsetDateTime = OffsetDateTime.now().plusHours(1),
    sluttTidspunkt: OffsetDateTime? = OffsetDateTime.now().plusHours(2),
    lokasjon: HendelseModel.Lokasjon? = HendelseModel.Lokasjon("foo", "bar", "baz"),
    erDigitalt: Boolean = true,
    eksterneVarsler: List<HendelseModel.EksterntVarsel> = listOf(),
    påminnelse: Påminnelse? = null,
    hardDelete: LocalDateTimeOrDuration? = null,
) = HendelseModel.KalenderavtaleOpprettet(
    merkelapp = merkelapp,
    eksternId = eksternId,
    mottakere = mottakere,
    hendelseId = notifikasjonId,
    notifikasjonId = notifikasjonId,
    tekst = tekst,
    grupperingsid = grupperingsid,
    lenke = lenke,
    opprettetTidspunkt = opprettetTidspunkt,
    virksomhetsnummer = virksomhetsnummer,
    kildeAppNavn = kildeAppNavn,
    produsentId = produsentId,
    eksterneVarsler = eksterneVarsler,
    hardDelete = hardDelete,
    sakId = sakId,
    tilstand = tilstand,
    startTidspunkt = startTidspunkt,
    sluttTidspunkt = sluttTidspunkt,
    lokasjon = lokasjon,
    erDigitalt = erDigitalt,
    påminnelse = påminnelse,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.kalenderavtaleOppdatert(
    notifikasjonId: UUID,
    virksomhetsnummer: String = TEST_VIRKSOMHET_1,
    produsentId: String = randomProdusentId(),
    kildeAppNavn: String = randomKildeAppNavn(),
    tekst: String? = null,
    lenke: String? = null,
    tilstand: KalenderavtaleTilstand? = null,
    startTidspunkt: OffsetDateTime? = null,
    sluttTidspunkt: OffsetDateTime? = null,
    lokasjon: HendelseModel.Lokasjon? = null,
    erDigitalt: Boolean = true,
    eksterneVarsler: List<HendelseModel.EksterntVarsel> = listOf(),
    påminnelse: Påminnelse? = null,
    hardDelete: HardDeleteUpdate? = null,
) = HendelseModel.KalenderavtaleOppdatert(
    hendelseId = notifikasjonId,
    notifikasjonId = notifikasjonId,
    tekst = tekst,
    lenke = lenke,
    virksomhetsnummer = virksomhetsnummer,
    kildeAppNavn = kildeAppNavn,
    produsentId = produsentId,
    eksterneVarsler = eksterneVarsler,
    hardDelete = hardDelete,
    tilstand = tilstand,
    startTidspunkt = startTidspunkt,
    sluttTidspunkt = sluttTidspunkt,
    lokasjon = lokasjon,
    erDigitalt = erDigitalt,
    påminnelse = påminnelse,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.oppgaveOpprettet(
    notifikasjonId: UUID = UUID.randomUUID(),
    virksomhetsnummer: String = TEST_VIRKSOMHET_1,
    produsentId: String = randomProdusentId(),
    kildeAppNavn: String = randomKildeAppNavn(),
    merkelapp: String = randomMerkelapp(),
    eksternId: String = UUID.randomUUID().toString(),
    mottakere: List<HendelseModel.Mottaker> = listOf(TEST_MOTTAKER_1),
    tekst: String = randomTekst("Oppgave-tekst"),
    grupperingsid: String? = null,
    lenke: String = randomLenke("oppgave"),
    opprettetTidspunkt: OffsetDateTime = TEST_OPPRETTET_TIDSPUNKT_1,
    eksterneVarsler: List<HendelseModel.EksterntVarsel> = listOf(),
    hardDelete: LocalDateTimeOrDuration? = null,
    frist: LocalDate? = null,
    påminnelse: Påminnelse? = null,
) = OppgaveOpprettet(
    virksomhetsnummer = virksomhetsnummer,
    notifikasjonId = notifikasjonId,
    hendelseId = notifikasjonId,
    produsentId = produsentId,
    kildeAppNavn = kildeAppNavn,
    merkelapp = merkelapp,
    eksternId = eksternId,
    mottakere = mottakere,
    tekst = tekst,
    grupperingsid = grupperingsid,
    lenke = lenke,
    opprettetTidspunkt = opprettetTidspunkt,
    eksterneVarsler = eksterneVarsler,
    hardDelete = hardDelete,
    frist = frist,
    påminnelse = påminnelse,
    sakId = null,
).also {
    oppdaterModellEtterHendelse(it, HendelseModel.HendelseMetadata(opprettetTidspunkt.toInstant()))
}

suspend fun BrukerRepository.oppgaveUtført(
    oppgave: OppgaveOpprettet,
    hardDelete: HardDeleteUpdate? = null,
    utfoertTidspunkt: OffsetDateTime = OffsetDateTime.MIN,
    nyLenke: String? = null,
) = OppgaveUtført(
    hendelseId = UUID.randomUUID(),
    notifikasjonId = oppgave.notifikasjonId,
    virksomhetsnummer = oppgave.virksomhetsnummer,
    produsentId = oppgave.produsentId,
    kildeAppNavn = oppgave.kildeAppNavn,
    hardDelete = hardDelete,
    utfoertTidspunkt = utfoertTidspunkt,
    nyLenke = nyLenke,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.oppgaveUtgått(
    oppgave: OppgaveOpprettet,
    hardDelete: HardDeleteUpdate? = null,
    utgaattTidspunkt: OffsetDateTime,
    nyLenke: String? = null,
) = OppgaveUtgått(
    hendelseId = UUID.randomUUID(),
    notifikasjonId = oppgave.notifikasjonId,
    virksomhetsnummer = oppgave.virksomhetsnummer,
    produsentId = oppgave.produsentId,
    kildeAppNavn = oppgave.kildeAppNavn,
    hardDelete = hardDelete,
    utgaattTidspunkt = utgaattTidspunkt,
    nyLenke = nyLenke,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.oppgaveFristUtsatt(
    oppgave: OppgaveOpprettet,
    frist: LocalDate,
    påminnelse: Påminnelse? = null,
) = FristUtsatt(
    hendelseId = UUID.randomUUID(),
    notifikasjonId = oppgave.notifikasjonId,
    virksomhetsnummer = oppgave.virksomhetsnummer,
    produsentId = oppgave.produsentId,
    kildeAppNavn = oppgave.kildeAppNavn,
    fristEndretTidspunkt = Instant.now(),
    frist = frist,
    påminnelse = påminnelse,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.påminnelseOpprettet(
    oppgave: OppgaveOpprettet,
    opprettetTidpunkt: Instant = Instant.now(),
    frist: LocalDate? = oppgave.frist,
    tidspunkt: HendelseModel.PåminnelseTidspunkt,
    eksterneVarsler: List<HendelseModel.EksterntVarsel> = listOf(),
) = HendelseModel.PåminnelseOpprettet(
    virksomhetsnummer = oppgave.virksomhetsnummer,
    hendelseId = UUID.randomUUID(),
    produsentId = oppgave.produsentId,
    kildeAppNavn = oppgave.kildeAppNavn,
    notifikasjonId = oppgave.notifikasjonId,
    opprettetTidpunkt = opprettetTidpunkt,
    fristOpprettetTidspunkt = oppgave.opprettetTidspunkt.toInstant(),
    frist = frist,
    tidspunkt = tidspunkt,
    eksterneVarsler = eksterneVarsler,
    bestillingHendelseId = oppgave.notifikasjonId,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.påminnelseOpprettet(
    oppgave: OppgaveOpprettet,
    konkretPåminnelseTidspunkt: LocalDateTime
) = HendelseModel.PåminnelseOpprettet(
    virksomhetsnummer = oppgave.virksomhetsnummer,
    hendelseId = UUID.randomUUID(),
    produsentId = oppgave.produsentId,
    kildeAppNavn = oppgave.kildeAppNavn,
    notifikasjonId = oppgave.notifikasjonId,
    opprettetTidpunkt = Instant.now(),
    fristOpprettetTidspunkt = oppgave.opprettetTidspunkt.toInstant(),
    frist = oppgave.frist,
    tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
        konkret = konkretPåminnelseTidspunkt,
        opprettetTidspunkt = oppgave.opprettetTidspunkt,
        frist = oppgave.frist
    ),
    eksterneVarsler = listOf(),
    bestillingHendelseId = oppgave.notifikasjonId,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun <H>BrukerRepository.brukerKlikket(
    notifikasjon: H,
    fnr: String = TEST_FNR_1,
) where H : HendelseModel.Hendelse, H : HendelseModel.Notifikasjon =
    HendelseModel.BrukerKlikket(
        virksomhetsnummer = notifikasjon.virksomhetsnummer,
        notifikasjonId = notifikasjon.aggregateId,
        hendelseId = UUID.randomUUID(),
        kildeAppNavn = notifikasjon.kildeAppNavn,
        fnr = fnr,
    ).also {
        oppdaterModellEtterHendelse(it)
    }

suspend fun BrukerRepository.sakOpprettet(
    sakId: UUID = UUID.randomUUID(),
    virksomhetsnummer: String = TEST_VIRKSOMHET_1,
    produsentId: String = randomProdusentId(),
    kildeAppNavn: String = randomKildeAppNavn(),
    merkelapp: String = randomMerkelapp(),
    lenke: String? = randomLenke("sak"),
    tittel: String = randomTekst("Sak-tittel"),
    mottakere: List<HendelseModel.Mottaker> = listOf(TEST_MOTTAKER_1),
    grupperingsid: String = UUID.randomUUID().toString(),
    oppgittTidspunkt: OffsetDateTime? = null,
    mottattTidspunkt: OffsetDateTime? = null,
    hardDelete: LocalDateTimeOrDuration? = null,
) = HendelseModel.SakOpprettet(
    hendelseId = sakId,
    sakId = sakId,
    virksomhetsnummer = virksomhetsnummer,
    produsentId = produsentId,
    kildeAppNavn = kildeAppNavn,
    grupperingsid = grupperingsid,
    merkelapp = merkelapp,
    mottakere = mottakere,
    tittel = tittel,
    lenke = lenke,
    oppgittTidspunkt = oppgittTidspunkt,
    mottattTidspunkt = mottattTidspunkt,
    hardDelete = hardDelete,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.nyStatusSak(
    sak: HendelseModel.SakOpprettet,
    hendelseId: UUID = UUID.randomUUID(),
    virksomhetsnummer: String = sak.virksomhetsnummer,
    produsentId: String = randomProdusentId(),
    kildeAppNavn: String = randomKildeAppNavn(),
    status: SakStatus = randomSakStatus(),
    overstyrStatustekstMed: String? = null,
    oppgittTidspunkt: OffsetDateTime? = null,
    mottattTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    idempotensKey: String,
    hardDelete: HardDeleteUpdate? = null,
    nyLenkeTilSak: String? = null,
) = NyStatusSak(
    hendelseId = hendelseId,
    virksomhetsnummer = virksomhetsnummer,
    produsentId = produsentId,
    kildeAppNavn = kildeAppNavn,
    sakId = sak.sakId,
    status = status,
    overstyrStatustekstMed = overstyrStatustekstMed,
    oppgittTidspunkt = oppgittTidspunkt,
    mottattTidspunkt = mottattTidspunkt,
    idempotensKey = idempotensKey,
    hardDelete = hardDelete,
    nyLenkeTilSak = nyLenkeTilSak,
).also {
    oppdaterModellEtterHendelse(it)
}
