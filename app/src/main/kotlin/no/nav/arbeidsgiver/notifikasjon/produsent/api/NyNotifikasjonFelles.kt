package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnReporteeMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRolleMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

internal data class EksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
) {

    fun tilDomene(virksomhetsnummer: String): EksterntVarsel {
        if (sms != null) {
            return sms.tilDomene(virksomhetsnummer)
        }
        if (epost != null) {
            return epost.tilDomene(virksomhetsnummer)
        }
        throw RuntimeException("Feil format")
    }

    data class Sms(
        val mottaker: Mottaker,
        val smsTekst: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {

        fun tilDomene(virksomhetsnummer: String): SmsVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somSakOpprettetHendelse()
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
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somSakOpprettetHendelse()
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

internal data class AltinnRolleMottakerInput(
    val roleDefinitionCode: String,
) {
    suspend fun tilDomene(
        virksomhetsnummer: String,
        finnRolleId: suspend (String) -> AltinnRolle?
    ): Mottaker =
        AltinnRolleMottaker(
            roleDefinitionCode = roleDefinitionCode,
            roleDefinitionId = finnRolleId(roleDefinitionCode)?.RoleDefinitionId
                ?: throw UkjentRolleException("klarte ikke finne altinnrolle $roleDefinitionCode"),
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

internal data class AltinnReporteeMottakerInput(
    val fnr: String,
) {
    fun tilDomene(virksomhetsnummer: String): Mottaker =
        AltinnReporteeMottaker(
            fnr = fnr,
            virksomhetsnummer = virksomhetsnummer
        )
}

internal class UkjentRolleException(message: String) : RuntimeException(message)


internal data class MottakerInput(
    val altinn: AltinnMottakerInput?,
    val altinnReportee: AltinnReporteeMottakerInput?,
    val naermesteLeder: NaermesteLederMottakerInput?,
    val altinnRolle: AltinnRolleMottakerInput?
) {

    suspend fun tilDomene(
        virksomhetsnummer: String,
        finnRolleId: suspend (String) -> AltinnRolle?
    ): Mottaker {
        check(listOfNotNull(altinn, naermesteLeder, altinnReportee,altinnRolle).size == 1) {
            "Ugyldig mottaker"
        }
        return altinn?.tilDomene(virksomhetsnummer)
            ?: (altinnReportee?.tilDomene(virksomhetsnummer)
                ?: (naermesteLeder?.tilDomene(virksomhetsnummer)
                    ?: (altinnRolle?.tilDomene(virksomhetsnummer, finnRolleId)
                        ?: throw IllegalArgumentException("Ugyldig mottaker"))))
    }
}

internal data class MetadataInput(
    val grupperingsid: String?,
    val eksternId: String,
    val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    val virksomhetsnummer: String,
    val hardDelete: FutureTemporalInput?,
)

internal data class NyEksternVarselResultat(
    val id: UUID,
)

internal fun EksterntVarselInput.SendetidspunktInput.somSakOpprettetHendelse(): Pair<EksterntVarselSendingsvindu, LocalDateTime?> {
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

