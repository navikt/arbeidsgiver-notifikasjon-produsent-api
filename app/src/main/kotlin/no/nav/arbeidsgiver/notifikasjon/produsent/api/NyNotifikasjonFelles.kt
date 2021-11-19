package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import java.time.OffsetDateTime
import java.util.*



data class EksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
) {

    fun tilDomene(): EksterntVarsel {
        basedOnEnv(
            prod = { throw RuntimeException("eksternt varsel ikke skrudd på i prod") },
            other = {}
        )

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
    ) {

        fun tilDomene(): SmsVarselKontaktinfo {
            if (mottaker.kontaktinfo != null) {
                return SmsVarselKontaktinfo(
                    varselId =  UUID.randomUUID(),
                    tlfnr = mottaker.kontaktinfo.tlf,
                    fnrEllerOrgnr = mottaker.kontaktinfo.fnr,
                    smsTekst = smsTekst,
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null,
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

    data class Epost(
        val mottaker: Mottaker,
        val epostTittel: String,
        val epostHtmlBody: String,
    ) {
        fun tilDomene(): EpostVarselKontaktinfo {
            if (mottaker.kontaktinfo != null) {
                return EpostVarselKontaktinfo(
                    varselId = UUID.randomUUID(),
                    epostAddr = mottaker.kontaktinfo.epostadresse,
                    fnrEllerOrgnr = mottaker.kontaktinfo.fnr,
                    tittel = epostTittel,
                    htmlBody = epostHtmlBody,
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null,
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
