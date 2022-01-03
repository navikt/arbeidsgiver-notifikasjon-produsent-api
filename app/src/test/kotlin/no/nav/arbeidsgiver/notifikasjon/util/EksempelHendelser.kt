package no.nav.arbeidsgiver.notifikasjon.util

import com.fasterxml.jackson.databind.node.NullNode
import no.nav.arbeidsgiver.notifikasjon.*
import java.time.OffsetDateTime


object EksempelHendelse {
    val BeskjedOpprettet = Hendelse.BeskjedOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
        mottaker = AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
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
        )
    )
    val OppgaveOpprettet = Hendelse.OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "1",
        eksternId = "1",
        mottaker = AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
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
        )
    )
    val OppgaveUtført = Hendelse.OppgaveUtført(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
    )
    val SoftDelete = Hendelse.SoftDelete(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        deletedAt = OffsetDateTime.now()
    )
    val HardDelete = Hendelse.HardDelete(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        deletedAt = OffsetDateTime.now()
    )
    val BrukerKlikket = Hendelse.BrukerKlikket(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        kildeAppNavn = "1",
        fnr = "42"
    )
    val EksterntVarselVellykket = Hendelse.EksterntVarselVellykket(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        varselId = uuid("1"),
        råRespons = NullNode.instance
    )
    val EksterntVarselFeilet = Hendelse.EksterntVarselFeilet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        varselId = uuid("1"),
        råRespons = NullNode.instance,
        altinnFeilkode = "42",
        feilmelding = "oops"
    )

    val Alle = listOf(
        BeskjedOpprettet,
        OppgaveOpprettet,
        OppgaveUtført,
        SoftDelete,
        HardDelete,
        BrukerKlikket,
        EksterntVarselVellykket,
        EksterntVarselFeilet,
    )
}
