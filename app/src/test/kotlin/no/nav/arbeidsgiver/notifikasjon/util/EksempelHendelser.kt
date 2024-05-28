package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.databind.node.NullNode
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*


object EksempelHendelse {
    private val hendelseId = generateSequence(0) { it + 1 }
        .map { uuid(it.toString()) }
        .iterator()

    private fun <T> withId(blokk: (id: UUID)-> T) : T = hendelseId.next().let { blokk.invoke(it) }

    val BeskjedOpprettet = withId { id ->
        BeskjedOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = id.toString(),
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                HendelseModel.AltinntjenesteVarselKontaktinfo(
                    varselId = uuid("5"),
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1",
                    tittel = "hey",
                    innhold = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            sakId = null,
        )
    }
    val BeskjedOpprettet_2_Mottakere = withId { id ->
        BeskjedOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = id.toString(),
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    naermesteLederFnr = "1",
                    ansattFnr = "1",
                    virksomhetsnummer = "1"
                )
            ),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            sakId = null,
        )
    }
    val BeskjedOpprettet_3_Mottakere = withId { id ->
        BeskjedOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = id.toString(),
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    naermesteLederFnr = "1",
                    ansattFnr = "1",
                    virksomhetsnummer = "1"
                ),
            ),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            sakId = null,
        )
    }
    val OppgaveOpprettet = withId { id ->
        HendelseModel.OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = id.toString(),
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            frist = null,
            påminnelse = null,
            sakId = null,
        )
    }
    val OppgaveOpprettet_2_Mottakere = withId { id ->
        HendelseModel.OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = id.toString(),
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    virksomhetsnummer = "1",
                    ansattFnr = "1",
                    naermesteLederFnr = "2"
                )
            ),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            frist = null,
            påminnelse = null,
            sakId = null,
        )
    }
    val OppgaveOpprettet_3_Mottakere = withId { id ->
        HendelseModel.OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = id.toString(),
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    virksomhetsnummer = "1",
                    ansattFnr = "1",
                    naermesteLederFnr = "2"
                ),
            ),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            frist = null,
            påminnelse = null,
            sakId = null,
        )
    }
    val OppgaveOpprettet_MedFrist = withId { id ->
        HendelseModel.OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = id.toString(),
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            frist = LocalDate.parse("2020-01-02"),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            påminnelse = null,
            sakId = null,
        )
    }
    val OppgaveOpprettet_MedPåminnelse = withId { id ->
        HendelseModel.OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = "2",
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            frist = LocalDate.parse("2020-01-22"),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            påminnelse = HendelseModel.Påminnelse(
                tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                    konkret = LocalDateTime.parse("2020-01-14T01:01"),
                    påminnelseTidspunkt = Instant.parse("2020-01-14T02:01:00.00Z"),
                ),
                eksterneVarsler = listOf()
            ),
            sakId = null,
        )
    }
    val OppgaveUtført = HendelseModel.OppgaveUtført(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        hardDelete = HendelseModel.HardDeleteUpdate(
            nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
        ),
        nyLenke = null,
        utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
    )
    val OppgaveUtgått = HendelseModel.OppgaveUtgått(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        utgaattTidspunkt = OffsetDateTime.now(),
        hardDelete = HendelseModel.HardDeleteUpdate(
            nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
        ),
        nyLenke = null,
    )
    val SoftDelete = HendelseModel.SoftDelete(
        virksomhetsnummer = "1",
        aggregateId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        deletedAt = OffsetDateTime.now(),
        grupperingsid = null,
        merkelapp = "tag",
    )
    val HardDelete = HendelseModel.HardDelete(
        virksomhetsnummer = "1",
        aggregateId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        deletedAt = OffsetDateTime.now(),
        grupperingsid = null,
        merkelapp = "tag",
    )
    val BrukerKlikket = HendelseModel.BrukerKlikket(
        virksomhetsnummer = "1",
        notifikasjonId = BeskjedOpprettet.notifikasjonId,
        hendelseId = hendelseId.next(),
        kildeAppNavn = "1",
        fnr = "42"
    )
    val BrukerKlikketPåDeletedNotifikasjon = HendelseModel.BrukerKlikket(
        virksomhetsnummer = "1",
        notifikasjonId = UUID.randomUUID(),
        hendelseId = hendelseId.next(),
        kildeAppNavn = "1",
        fnr = "42"
    )
    val EksterntVarselVellykket = HendelseModel.EksterntVarselVellykket(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        varselId = uuid("1"),
        råRespons = NullNode.instance
    )
    val EksterntVarselFeilet = HendelseModel.EksterntVarselFeilet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        varselId = uuid("1"),
        råRespons = NullNode.instance,
        altinnFeilkode = "42",
        feilmelding = "oops"
    )
    val EksterntVarselKansellert = HendelseModel.EksterntVarselKansellert(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        varselId = uuid("1"),
    )
    val SakOpprettet = withId { id ->
        HendelseModel.SakOpprettet(
            hendelseId = id,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = id,
            grupperingsid = id.toString(),
            merkelapp = "tag",
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    virksomhetsnummer = "1",
                    ansattFnr = "1",
                    naermesteLederFnr = "2"
                ),
            ),
            tittel = "foo",
            lenke = "#foo",
            oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            mottattTidspunkt = OffsetDateTime.now(),
            nesteSteg = null,
            hardDelete = null,
        )
    }
    val SakOpprettetNullOppgittTs = withId { id ->
        HendelseModel.SakOpprettet(
            hendelseId = id,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = id,
            grupperingsid = id.toString(),
            merkelapp = "tag",
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    virksomhetsnummer = "1",
                    ansattFnr = "1",
                    naermesteLederFnr = "2"
                ),
            ),
            tittel = "foo",
            lenke = "#foo",
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.now(),
            nesteSteg = null,
            hardDelete = null,
        )
    }
    val SakOpprettetNullOppgittLenke = withId { id ->
        HendelseModel.SakOpprettet(
            hendelseId = id,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = id,
            grupperingsid = id.toString(),
            merkelapp = "tag",
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    virksomhetsnummer = "1",
                    ansattFnr = "1",
                    naermesteLederFnr = "2"
                ),
            ),
            tittel = "foo",
            lenke = null,
            oppgittTidspunkt = null,
            mottattTidspunkt = OffsetDateTime.now(),
            nesteSteg = null,
            hardDelete = null,
        )
    }
    val NyStatusSak = HendelseModel.NyStatusSak(
        hendelseId = hendelseId.next(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = SakOpprettet.sakId,
        status = HendelseModel.SakStatus.MOTTATT,
        overstyrStatustekstMed = "noe",
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
        hardDelete = HendelseModel.HardDeleteUpdate(
            nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
        ),
        nyLenkeTilSak = null,
    )

    val NyStatusSak_NullOppgittTs = HendelseModel.NyStatusSak(
        hendelseId = hendelseId.next(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = SakOpprettet.sakId,
        status = HendelseModel.SakStatus.MOTTATT,
        overstyrStatustekstMed = "noe",
        oppgittTidspunkt = null,
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
        hardDelete = HendelseModel.HardDeleteUpdate(
            nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
        ),
        nyLenkeTilSak = null,
    )
    val PåminnelseOpprettet = HendelseModel.PåminnelseOpprettet(
        virksomhetsnummer = "1",
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        notifikasjonId = uuid("1"),
        opprettetTidpunkt = Instant.now(),
        fristOpprettetTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z").toInstant(),
        frist = LocalDate.parse("2021-01-14"),
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
            konkret = LocalDateTime.parse("2021-01-10T12:00:00"),
            opprettetTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
            frist = LocalDate.parse("2021-01-14"),
            startTidspunkt = null,
        ),
        eksterneVarsler = listOf(),
        bestillingHendelseId = uuid("1"),
    )
    val KalenderavtaleOpprettet = withId { id ->
        HendelseModel.KalenderavtaleOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = id,
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "tag",
            grupperingsid = SakOpprettet.grupperingsid,
            eksternId = "1",
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                ),
                NærmesteLederMottaker(
                    virksomhetsnummer = "1",
                    ansattFnr = "1",
                    naermesteLederFnr = "2"
                ),
            ),
            hardDelete = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            påminnelse = HendelseModel.Påminnelse(
                tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                    konkret = LocalDateTime.parse("2020-01-14T01:01"),
                    påminnelseTidspunkt = Instant.parse("2020-01-14T02:01:00.00Z"),
                ),
                eksterneVarsler = listOf(
                    SmsVarselKontaktinfo(
                        varselId = uuid("3"),
                        fnrEllerOrgnr = "1",
                        tlfnr = "1",
                        smsTekst = "hey",
                        sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                        sendeTidspunkt = null
                    ),
                    EpostVarselKontaktinfo(
                        varselId = uuid("4"),
                        fnrEllerOrgnr = "1",
                        epostAddr = "1",
                        tittel = "hey",
                        htmlBody = "body",
                        sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                        sendeTidspunkt = null
                    ),
                )
            ),
            sakId = SakOpprettet.sakId,
            lenke = "https://foo.no",
            tekst = "foo",
            opprettetTidspunkt = OffsetDateTime.now(),
            tilstand = VENTER_SVAR_FRA_ARBEIDSGIVER,
            startTidspunkt = LocalDateTime.now().plusHours(1),
            sluttTidspunkt = LocalDateTime.now().plusHours(2),
            lokasjon = HendelseModel.Lokasjon("foo", "bar", "baz"),
            erDigitalt = true,
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                HendelseModel.AltinntjenesteVarselKontaktinfo(
                    varselId = uuid("5"),
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1",
                    tittel = "hey",
                    innhold = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                )
            ),
        )
    }
    val KalenderavtaleOppdatert = HendelseModel.KalenderavtaleOppdatert(
        virksomhetsnummer = "1",
        notifikasjonId = KalenderavtaleOpprettet.notifikasjonId,
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        hardDelete = HendelseModel.HardDeleteUpdate(
            nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
        ),
        påminnelse = HendelseModel.Påminnelse(
            tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                konkret = LocalDateTime.parse("2020-01-14T01:01"),
                påminnelseTidspunkt = Instant.parse("2020-01-14T02:01:00.00Z"),
            ),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
                EpostVarselKontaktinfo(
                    varselId = uuid("4"),
                    fnrEllerOrgnr = "1",
                    epostAddr = "1",
                    tittel = "hey",
                    htmlBody = "body",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null
                ),
            )
        ),
        lenke = "https://foo.no",
        tekst = "foo",
        tilstand = VENTER_SVAR_FRA_ARBEIDSGIVER,
        startTidspunkt = LocalDateTime.now().plusHours(1),
        sluttTidspunkt = LocalDateTime.now().plusHours(2),
        lokasjon = HendelseModel.Lokasjon("foo", "bar", "baz"),
        erDigitalt = true,
        eksterneVarsler = listOf(
            SmsVarselKontaktinfo(
                varselId = uuid("3"),
                fnrEllerOrgnr = "1",
                tlfnr = "1",
                smsTekst = "hey",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            EpostVarselKontaktinfo(
                varselId = uuid("4"),
                fnrEllerOrgnr = "1",
                epostAddr = "1",
                tittel = "hey",
                htmlBody = "body",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            HendelseModel.AltinntjenesteVarselKontaktinfo(
                varselId = uuid("5"),
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1",
                tittel = "hey",
                innhold = "body",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            )
        ),
        idempotenceKey = UUID.randomUUID().toString(),
        grupperingsid = KalenderavtaleOpprettet.grupperingsid,
        oppdatertTidspunkt = Instant.now(),
        opprettetTidspunkt = KalenderavtaleOpprettet.opprettetTidspunkt.toInstant(),
        merkelapp = KalenderavtaleOpprettet.merkelapp
    )

    val SakOpprettet_UtenNesteSteg = HendelseModel.SakOpprettet(
        hendelseId = uuid("1"),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("1"),
        grupperingsid = "1",
        merkelapp = "tag",
        mottakere = listOf(
            AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            ),
            NærmesteLederMottaker(
                virksomhetsnummer = "1",
                ansattFnr = "1",
                naermesteLederFnr = "2"
            ),
        ),
        tittel = "foo",
        nesteSteg = null,
        lenke = "#foo",
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    )

    val SakOpprettet_MedNesteSteg =  HendelseModel.SakOpprettet(
        hendelseId = uuid("2"),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("2"),
        grupperingsid = "2",
        merkelapp = "tag",
        mottakere = listOf(
            AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            ),
            NærmesteLederMottaker(
                virksomhetsnummer = "1",
                ansattFnr = "1",
                naermesteLederFnr = "2"
            ),
        ),
        tittel = "foo",
        nesteSteg = "Neste steg",
        lenke = "#foo",
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    )

    val NesteStegSak_TilNull = HendelseModel.NesteStegSak (
        virksomhetsnummer = "1",
        sakId = SakOpprettet_UtenNesteSteg.sakId,
        nesteSteg = null,
        grupperingsid = SakOpprettet_UtenNesteSteg.grupperingsid,
        merkelapp = SakOpprettet_UtenNesteSteg.merkelapp,
        hendelseId = uuid("01"),
        produsentId = "1",
        kildeAppNavn = "1",
        idempotenceKey = UUID.randomUUID().toString(),
    )

    val NesteStegSak_NyttNesteSteg = HendelseModel.NesteStegSak (
        virksomhetsnummer = SakOpprettet_MedNesteSteg.virksomhetsnummer,
        sakId = SakOpprettet_MedNesteSteg.sakId,
        nesteSteg = "Nytt neste steg",
        grupperingsid = SakOpprettet_MedNesteSteg.grupperingsid,
        merkelapp = SakOpprettet_MedNesteSteg.merkelapp,
        hendelseId = uuid("02"),
        produsentId = "1",
        kildeAppNavn = "1",
        idempotenceKey = UUID.randomUUID().toString(),
    )

    val Alle: List<Hendelse> = listOf(
        BeskjedOpprettet,
        BeskjedOpprettet_2_Mottakere,
        BeskjedOpprettet_3_Mottakere,
        OppgaveOpprettet,
        OppgaveOpprettet_2_Mottakere,
        OppgaveOpprettet_3_Mottakere,
        OppgaveOpprettet_MedFrist,
        OppgaveOpprettet_MedPåminnelse,
        OppgaveUtført,
        OppgaveUtgått,
        SoftDelete,
        HardDelete,
        BrukerKlikket,
        BrukerKlikketPåDeletedNotifikasjon,
        EksterntVarselVellykket,
        EksterntVarselFeilet,
        EksterntVarselKansellert,
        SakOpprettet,
        SakOpprettetNullOppgittTs,
        NyStatusSak,
        NyStatusSak_NullOppgittTs,
        SakOpprettetNullOppgittLenke,
        PåminnelseOpprettet,
        KalenderavtaleOpprettet,
        KalenderavtaleOppdatert,
        SakOpprettet_UtenNesteSteg,
        SakOpprettet_MedNesteSteg,
        NesteStegSak_TilNull,
        NesteStegSak_NyttNesteSteg
    )
}
