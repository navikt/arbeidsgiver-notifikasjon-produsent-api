package no.nav.arbeidsgiver.notifikasjon.produsent.api

import java.time.OffsetDateTime
import java.util.*

sealed interface NyNotifikasjonError :
    MutationNyBeskjed.NyBeskjedResultat,
    MutationNyOppgave.NyOppgaveResultat


data class EpostMottakerInput(
    val kontaktinfo: EpostKontaktinfoInput?
)

data class EpostKontaktinfoInput(
    val fnr: String,
    val epost: String,
)

data class EksternVarselInput(
    val sms: EksternVarselSmsInput?,
    val epost: EksternVarselEpostInput?,
)

data class EksternVarselSmsInput(
    val mottaker: SmsMottakerInput,
    val smsTekst: String
)

data class SmsMottakerInput(
    val kontaktinfo: SmsKontaktinfoInput?
)

data class SmsKontaktinfoInput(
    val fnr: String,
    val tlf: String,
)

data class EksternVarselEpostInput(
    val mottaker: EpostMottakerInput,
    val epostTittel: String,
    val epostBodyHtml: String,
)


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
