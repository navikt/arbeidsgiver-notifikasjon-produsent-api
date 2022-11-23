package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.databind.node.NullNode
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnReporteeMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime


object EksempelHendelse {
    private val hendelseId = generateSequence(0) { it + 1 }
        .map { uuid(it.toString()) }
        .iterator()

    val BeskjedOpprettet = BeskjedOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
        mottakere = listOf(AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )),
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
    )
    val BeskjedOpprettet_2_Mottakere = BeskjedOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
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
    )
    val BeskjedOpprettet_3_Mottakere = BeskjedOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
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
            AltinnReporteeMottaker(
                fnr = "1",
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
    )
    val OppgaveOpprettet = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
        mottakere = listOf(AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )),
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
    )
    val OppgaveOpprettet_2_Mottakere = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
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
    )
    val OppgaveOpprettet_3_Mottakere = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
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
            AltinnReporteeMottaker(
                fnr = "1",
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
        frist = null,
        påminnelse = null,
    )
    val OppgaveOpprettet_MedFrist = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("3"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "2",
        mottakere = listOf(AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )),
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
    )
    val OppgaveOpprettet_MedPåminnelse = HendelseModel.OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("3"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "2",
        mottakere = listOf(AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )),
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
                opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
                frist = LocalDate.parse("2020-01-22")
            ),
            eksterneVarsler = listOf()
        )
    )
    val OppgaveUtført = HendelseModel.OppgaveUtført(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        hardDelete = HendelseModel.HardDeleteUpdate(
            nyTid = HendelseModel.LocalDateTimeOrDuration.LocalDateTime(LocalDateTime.parse("2019-10-13T07:20:50.52")),
            strategi = HendelseModel.NyTidStrategi.OVERSKRIV,
        )
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
        )
    )
    val SoftDelete = HendelseModel.SoftDelete(
        virksomhetsnummer = "1",
        aggregateId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        deletedAt = OffsetDateTime.now()
    )
    val HardDelete = HendelseModel.HardDelete(
        virksomhetsnummer = "1",
        aggregateId = uuid("1"),
        hendelseId = hendelseId.next(),
        produsentId = "1",
        kildeAppNavn = "1",
        deletedAt = OffsetDateTime.now()
    )
    val BrukerKlikket = HendelseModel.BrukerKlikket(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
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
    val SakOpprettet = HendelseModel.SakOpprettet(
        hendelseId = hendelseId.next(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("0"),
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
            AltinnReporteeMottaker(
                fnr = "1",
                virksomhetsnummer = "1"
            )
        ),
        tittel = "foo",
        lenke = "#foo",
        oppgittTidspunkt = OffsetDateTime.parse("2021-01-01T13:37:00Z"),
        mottattTidspunkt = OffsetDateTime.now(),
        hardDelete = null,
    )
    val NyStatusSak = HendelseModel.NyStatusSak(
        hendelseId = hendelseId.next(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = uuid("0"),
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
        EksterntVarselVellykket,
        EksterntVarselFeilet,
        SakOpprettet,
        NyStatusSak,
    )
}
