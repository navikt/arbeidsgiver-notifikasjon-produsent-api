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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
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
const val TEST_RESSURS_ID_1 = "nav_test_testressurs1"

val TEST_MOTTAKER_1 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = TEST_VIRKSOMHET_1,
    serviceCode = TEST_SERVICE_CODE_1,
    serviceEdition = TEST_SERVICE_EDITION_1,
)

val TEST_MOTTAKER_2 = HendelseModel.AltinnRessursMottaker(
    virksomhetsnummer = TEST_VIRKSOMHET_2,
    ressursId = TEST_RESSURS_ID_1,
)

val TEST_TILGANG_1 = AltinnTilgang(
    orgNr = TEST_VIRKSOMHET_1 ,
    tilgang = "$TEST_SERVICE_CODE_1:$TEST_SERVICE_EDITION_1",
)

val TEST_TILGANG_2 = AltinnTilgang(
    orgNr = TEST_VIRKSOMHET_2 ,
    tilgang = TEST_RESSURS_ID_1,
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
    sak: HendelseModel.SakOpprettet? = null,
    notifikasjonId: UUID = UUID.randomUUID(),
    virksomhetsnummer: String = sak?.virksomhetsnummer ?: TEST_VIRKSOMHET_1,
    produsentId: String = sak?.produsentId ?: randomProdusentId(),
    kildeAppNavn: String = sak?.kildeAppNavn ?: randomKildeAppNavn(),
    merkelapp: String = sak?.merkelapp ?: randomMerkelapp(),
    eksternId: String = UUID.randomUUID().toString(),
    mottakere: List<HendelseModel.Mottaker> = sak?.mottakere ?: listOf(TEST_MOTTAKER_1),
    tekst: String = randomTekst("Beskjed-tekst"),
    grupperingsid: String? = sak?.grupperingsid,
    lenke: String = randomLenke("beskjed"),
    opprettetTidspunkt: OffsetDateTime = TEST_OPPRETTET_TIDSPUNKT_1,
    eksterneVarsler: List<HendelseModel.EksterntVarsel> = listOf(),
    hardDelete: LocalDateTimeOrDuration? = null,
    sakId: UUID? = sak?.sakId,
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
    sakId = sakId,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.kalenderavtaleOpprettet(
    sak: HendelseModel.SakOpprettet? = null,
    notifikasjonId: UUID = UUID.randomUUID(),
    sakId: UUID = sak?.sakId ?: UUID.randomUUID(),
    virksomhetsnummer: String = sak?.virksomhetsnummer ?: TEST_VIRKSOMHET_1,
    produsentId: String = sak?.produsentId ?: randomProdusentId(),
    kildeAppNavn: String = sak?.kildeAppNavn ?: randomKildeAppNavn(),
    merkelapp: String = sak?.merkelapp ?: randomMerkelapp(),
    eksternId: String = UUID.randomUUID().toString(),
    mottakere: List<HendelseModel.Mottaker> = sak?.mottakere ?: listOf(TEST_MOTTAKER_1),
    tekst: String = randomTekst("kalenderavtale-tekst"),
    grupperingsid: String = sak?.grupperingsid ?: randomTekst("kalenderavtale-grupperingsid"),
    lenke: String = randomLenke("kalenderavtale"),
    opprettetTidspunkt: OffsetDateTime = TEST_OPPRETTET_TIDSPUNKT_1,
    tilstand: KalenderavtaleTilstand = KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER,
    startTidspunkt: LocalDateTime = LocalDateTime.now().plusHours(1),
    sluttTidspunkt: LocalDateTime? = LocalDateTime.now().plusHours(2),
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
    startTidspunkt: LocalDateTime? = null,
    sluttTidspunkt: LocalDateTime? = null,
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
    idempotenceKey = null,
    grupperingsid = randomTekst("kalenderavtale-grupperingsid"),
    oppdatertTidspunkt = Instant.now(),
    opprettetTidspunkt = TEST_OPPRETTET_TIDSPUNKT_1.toInstant(),
    merkelapp = randomMerkelapp(),
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.oppgaveOpprettet(
    sak: HendelseModel.SakOpprettet? = null,
    notifikasjonId: UUID = UUID.randomUUID(),
    virksomhetsnummer: String = sak?.virksomhetsnummer ?: TEST_VIRKSOMHET_1,
    produsentId: String = sak?.produsentId ?: randomProdusentId(),
    kildeAppNavn: String = sak?.kildeAppNavn ?: randomKildeAppNavn(),
    merkelapp: String = sak?.merkelapp ?: randomMerkelapp(),
    eksternId: String = UUID.randomUUID().toString(),
    mottakere: List<HendelseModel.Mottaker> = sak?.mottakere ?: listOf(TEST_MOTTAKER_1),
    tekst: String = randomTekst("Oppgave-tekst"),
    grupperingsid: String? = sak?.grupperingsid,
    lenke: String = randomLenke("oppgave"),
    opprettetTidspunkt: OffsetDateTime = TEST_OPPRETTET_TIDSPUNKT_1,
    eksterneVarsler: List<HendelseModel.EksterntVarsel> = listOf(),
    hardDelete: LocalDateTimeOrDuration? = null,
    frist: LocalDate? = null,
    påminnelse: Påminnelse? = null,
    sakId: UUID? = sak?.sakId,
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
    sakId = sakId,
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
    merkelapp = oppgave.merkelapp
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
        notifikasjonOpprettetTidspunkt = oppgave.opprettetTidspunkt,
        frist = oppgave.frist,
        startTidspunkt = null,
    ),
    eksterneVarsler = listOf(),
    bestillingHendelseId = oppgave.notifikasjonId,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.påminnelseOpprettet(
    kalenderavtale: HendelseModel.KalenderavtaleOpprettet,
    konkretPåminnelseTidspunkt: LocalDateTime
) = HendelseModel.PåminnelseOpprettet(
    virksomhetsnummer = kalenderavtale.virksomhetsnummer,
    hendelseId = UUID.randomUUID(),
    produsentId = kalenderavtale.produsentId,
    kildeAppNavn = kalenderavtale.kildeAppNavn,
    notifikasjonId = kalenderavtale.notifikasjonId,
    opprettetTidpunkt = Instant.now(),
    fristOpprettetTidspunkt = kalenderavtale.opprettetTidspunkt.toInstant(),
    frist = null,
    tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
        konkret = konkretPåminnelseTidspunkt,
        notifikasjonOpprettetTidspunkt = kalenderavtale.opprettetTidspunkt,
        frist = null,
        startTidspunkt = kalenderavtale.startTidspunkt,
    ),
    eksterneVarsler = listOf(),
    bestillingHendelseId = kalenderavtale.notifikasjonId,
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
    tilleggsinformasjon: String? = null,
    mottakere: List<HendelseModel.Mottaker> = listOf(TEST_MOTTAKER_1),
    grupperingsid: String = UUID.randomUUID().toString(),
    oppgittTidspunkt: OffsetDateTime? = null,
    mottattTidspunkt: OffsetDateTime? = null,
    nesteSteg: String? = null,
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
    nesteSteg = nesteSteg,
    hardDelete = hardDelete,
    tilleggsinformasjon = tilleggsinformasjon
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
) = nyStatusSak(
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

suspend fun BrukerRepository.nyStatusSak(
    sakId: UUID,
    virksomhetsnummer: String,
    hendelseId: UUID = UUID.randomUUID(),
    produsentId: String = randomProdusentId(),
    kildeAppNavn: String = randomKildeAppNavn(),
    status: SakStatus = randomSakStatus(),
    overstyrStatustekstMed: String? = null,
    oppgittTidspunkt: OffsetDateTime? = null,
    mottattTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    idempotensKey: String = IdempotenceKey.initial(),
    hardDelete: HardDeleteUpdate? = null,
    nyLenkeTilSak: String? = null,
) = NyStatusSak(
    hendelseId = hendelseId,
    virksomhetsnummer = virksomhetsnummer,
    produsentId = produsentId,
    kildeAppNavn = kildeAppNavn,
    sakId = sakId,
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

suspend fun BrukerRepository.nesteStegSak(
    sak: HendelseModel.SakOpprettet,
    hendelseId: UUID = UUID.randomUUID(),
    virksomhetsnummer: String = sak.virksomhetsnummer,
    produsentId: String = randomProdusentId(),
    idempotensKey: String = UUID.randomUUID().toString(),
    nesteSteg: String? = randomTekst("neste-steg")
) = HendelseModel.NesteStegSak(
    hendelseId = hendelseId,
    virksomhetsnummer = virksomhetsnummer,
    produsentId = produsentId,
    kildeAppNavn = randomKildeAppNavn(),
    sakId = sak.sakId,
    nesteSteg = nesteSteg,
    idempotenceKey = idempotensKey,
    grupperingsid = sak.grupperingsid,
    merkelapp = sak.merkelapp,
).also {
    oppdaterModellEtterHendelse(it)
}

suspend fun BrukerRepository.softDelete(
    sak: HendelseModel.SakOpprettet? = null,
    virksomhetsnummer: String = sak?.virksomhetsnummer ?: TEST_VIRKSOMHET_1,
    aggregateId: UUID = sak?.sakId ?: UUID.randomUUID(),
    hendelseId: UUID = UUID.randomUUID(),
    produsentId: String = sak?.produsentId ?: randomProdusentId(),
    kildeAppNavn: String = sak?.kildeAppNavn ?: randomKildeAppNavn(),
    deletedAt: OffsetDateTime = OffsetDateTime.now(),
    grupperingsid: String? = sak?.grupperingsid,
    merkelapp: String? = sak?.merkelapp,
) = HendelseModel.SoftDelete(
    virksomhetsnummer = virksomhetsnummer,
    aggregateId = aggregateId,
    hendelseId = hendelseId,
    produsentId = produsentId,
    kildeAppNavn = kildeAppNavn,
    deletedAt = deletedAt,
    grupperingsid = grupperingsid,
    merkelapp = merkelapp,
).also {
    oppdaterModellEtterHendelse(it)
}