package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.SmsVarselKontaktinfo
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

data class EksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
) {

    fun tilDomene(): EksterntVarsel {
        if (sms != null) {
            return sms.tilDomene()
        }
        if (epost != null) {
            return epost.tilDomene()
        }
        throw RuntimeException("Feil format")
    }

    data class Sms(
        val mottaker: Mottaker,
        val smsTekst: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {

        fun tilDomene(): SmsVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.tilDomene()
            if (mottaker.kontaktinfo != null) {
                return SmsVarselKontaktinfo(
                    varselId =  UUID.randomUUID(),
                    tlfnr = mottaker.kontaktinfo.tlf,
                    fnrEllerOrgnr = mottaker.kontaktinfo.fnr,
                    smsTekst = smsTekst,
                    sendevindu = sendevindu,
                    sendeTidspunkt = sendeTidspunkt,
                )
            }
            throw RuntimeException("mottaker-felt mangler for sms")
        }

        data class Mottaker(
            val kontaktinfo: Kontaktinfo?
        )

        data class Kontaktinfo(
            val fnr: String,
            val tlf: String,
        )
    }

    data class SendetidspunktInput(
        val tidspunkt: LocalDateTime?,
        val sendevindu: Sendevindu?,
    )

    @Suppress("unused") // kommer som graphql input
    enum class Sendevindu(
        val somDomene: EksterntVarselSendingsvindu
    ) {
        NKS_AAPNINGSTID(EksterntVarselSendingsvindu.NKS_ÅPNINGSTID),
        DAGTID_IKKE_SOENDAG(EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG),
        LOEPENDE(EksterntVarselSendingsvindu.LØPENDE),
    }

    data class Epost(
        val mottaker: Mottaker,
        val epostTittel: String,
        val epostHtmlBody: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {
        fun tilDomene(): EpostVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.tilDomene()
            if (mottaker.kontaktinfo != null) {
                return EpostVarselKontaktinfo(
                    varselId = UUID.randomUUID(),
                    epostAddr = mottaker.kontaktinfo.epostadresse,
                    fnrEllerOrgnr = mottaker.kontaktinfo.fnr,
                    tittel = epostTittel,
                    htmlBody = epostHtmlBody,
                    sendevindu = sendevindu,
                    sendeTidspunkt = sendeTidspunkt
                )
            }
            throw RuntimeException("mottaker mangler for epost")
        }

        data class Mottaker(
            val kontaktinfo: Kontaktinfo?
        )

        data class Kontaktinfo(
            val fnr: String,
            val epostadresse: String,
        )
    }
}

data class NaermesteLederMottakerInput(
    val naermesteLederFnr: String,
    val ansattFnr: String,
    val virksomhetsnummer: String
) {
    fun tilDomene(): no.nav.arbeidsgiver.notifikasjon.Mottaker =
        no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker(
            naermesteLederFnr = naermesteLederFnr,
            ansattFnr = ansattFnr,
            virksomhetsnummer = virksomhetsnummer
        )
}

data class AltinnMottakerInput(
    val serviceCode: String,
    val serviceEdition: String,
    val virksomhetsnummer: String,
) {
    fun tilDomene(): no.nav.arbeidsgiver.notifikasjon.Mottaker =
        no.nav.arbeidsgiver.notifikasjon.AltinnMottaker(
            serviceCode = serviceCode,
            serviceEdition = serviceEdition,
            virksomhetsnummer = virksomhetsnummer
        )
}

data class MottakerInput(
    val altinn: AltinnMottakerInput?,
    val naermesteLeder: NaermesteLederMottakerInput?
) {

    fun tilDomene(): no.nav.arbeidsgiver.notifikasjon.Mottaker {
        return if (altinn != null && naermesteLeder == null) {
            altinn.tilDomene()
        } else if (naermesteLeder != null && altinn == null) {
            naermesteLeder.tilDomene()
        } else {
            throw IllegalArgumentException("Ugyldig mottaker")
        }
    }
}

data class MetadataInput(
    val grupperingsid: String?,
    val eksternId: String,
    val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
)

data class NyEksternVarselResultat(
    val id: UUID,
)

fun EksterntVarselInput.SendetidspunktInput.tilDomene(): Pair<EksterntVarselSendingsvindu, LocalDateTime?> {
    val sendevindu = this.sendevindu?.somDomene ?: EksterntVarselSendingsvindu.SPESIFISERT

    if (sendevindu == EksterntVarselSendingsvindu.SPESIFISERT) {
        if (tidspunkt == null) {
            throw RuntimeException("SPESIFISERT sendevindu krever tidspunkt, men er null")
        } else {
            return Pair(sendevindu, this.tidspunkt)
        }
    }

    if (tidspunkt != null) {
        throw RuntimeException("$sendevindu bruker ikke tidspunkt, men det er oppgitt")
    } else {
        return Pair(sendevindu, null)
    }
}

