package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

internal data class EksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
    val altinntjeneste: Altinntjeneste?,
) {

    fun tilDomene(virksomhetsnummer: String): EksterntVarsel {
        if (sms != null) {
            return sms.tilDomene(virksomhetsnummer)
        }
        if (epost != null) {
            return epost.tilDomene(virksomhetsnummer)
        }
        if (altinntjeneste != null) {
            return altinntjeneste.tilDomene(virksomhetsnummer)
        }
        throw RuntimeException("Feil format")
    }

    data class Sms(
        val mottaker: Mottaker,
        val smsTekst: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {

        fun tilDomene(virksomhetsnummer: String): SmsVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somVinduOgTidspunkt()
            if (mottaker.kontaktinfo != null) {
                return SmsVarselKontaktinfo(
                    varselId = UUID.randomUUID(),
                    tlfnr = mottaker.kontaktinfo.tlf,
                    fnrEllerOrgnr = virksomhetsnummer,
                    smsTekst = smsTekst,
                    sendevindu = sendevindu,
                    sendeTidspunkt = sendeTidspunkt,
                )
            }
            throw RuntimeException("mottaker-felt mangler for sms")
        }

        data class Mottaker(
            val kontaktinfo: Kontaktinfo?,
        )

        data class Kontaktinfo(
            val fnr: String?,
            val tlf: String,
        )
    }

    data class SendetidspunktInput(
        val tidspunkt: LocalDateTime?,
        val sendevindu: Sendevindu?,
    )

    @Suppress("unused") // kommer som graphql input
    enum class Sendevindu(
        val somDomene: EksterntVarselSendingsvindu,
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
        fun tilDomene(virksomhetsnummer: String): EpostVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somVinduOgTidspunkt()
            if (mottaker.kontaktinfo != null) {
                return EpostVarselKontaktinfo(
                    varselId = UUID.randomUUID(),
                    epostAddr = mottaker.kontaktinfo.epostadresse,
                    fnrEllerOrgnr = virksomhetsnummer,
                    tittel = epostTittel,
                    htmlBody = epostHtmlBody,
                    sendevindu = sendevindu,
                    sendeTidspunkt = sendeTidspunkt
                )
            }
            throw RuntimeException("mottaker mangler for epost")
        }

        data class Mottaker(
            val kontaktinfo: Kontaktinfo?,
        )

        data class Kontaktinfo(
            val fnr: String?,
            val epostadresse: String,
        )
    }

    data class Altinntjeneste(
        val mottaker: Mottaker,
        val tittel: String,
        val innhold: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {
        fun tilDomene(virksomhetsnummer: String): AltinntjenesteVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somVinduOgTidspunkt()
            return AltinntjenesteVarselKontaktinfo(
                varselId = UUID.randomUUID(),
                serviceCode = mottaker.serviceCode,
                serviceEdition = mottaker.serviceEdition,
                virksomhetsnummer = virksomhetsnummer,
                tittel = tittel.ensureSuffix(". "),
                innhold = innhold,
                sendevindu = sendevindu,
                sendeTidspunkt = sendeTidspunkt
            )
        }

        data class Mottaker(
            val serviceCode: String,
            val serviceEdition: String,
        )
    }
}

internal data class NaermesteLederMottakerInput(
    val naermesteLederFnr: String,
    val ansattFnr: String,
) {
    fun tilDomene(virksomhetsnummer: String): Mottaker =
        NærmesteLederMottaker(
            naermesteLederFnr = naermesteLederFnr,
            ansattFnr = ansattFnr,
            virksomhetsnummer = virksomhetsnummer
        )
}

internal data class AltinnMottakerInput(
    val serviceCode: String,
    val serviceEdition: String,
) {
    fun tilDomene(virksomhetsnummer: String): Mottaker =
        AltinnMottaker(
            serviceCode = serviceCode,
            serviceEdition = serviceEdition,
            virksomhetsnummer = virksomhetsnummer
        )
}

internal class UkjentRolleException(message: String) : RuntimeException(message)


internal data class MottakerInput(
    val altinn: AltinnMottakerInput?,
    val naermesteLeder: NaermesteLederMottakerInput?,
) {

    fun tilDomene(
        virksomhetsnummer: String,
    ): Mottaker {
        check(listOfNotNull(altinn, naermesteLeder).size == 1) {
            "Ugyldig mottaker"
        }
        return altinn?.tilDomene(virksomhetsnummer)
                ?: (naermesteLeder?.tilDomene(virksomhetsnummer)
                        ?: throw IllegalArgumentException("Ugyldig mottaker"))
    }
}

internal data class MetadataInput(
    val grupperingsid: String?,
    val eksternId: String,
    val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    val virksomhetsnummer: String,
    val hardDelete: FutureTemporalInput?,
)

internal data class NyEksterntVarselResultat(
    val id: UUID,
)

internal fun EksterntVarselInput.SendetidspunktInput.somVinduOgTidspunkt(): Pair<EksterntVarselSendingsvindu, LocalDateTime?> {
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

