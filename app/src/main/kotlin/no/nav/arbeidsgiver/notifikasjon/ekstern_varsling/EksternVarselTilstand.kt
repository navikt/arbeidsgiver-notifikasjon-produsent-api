package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.Hendelse
import java.util.*

sealed interface EksternVarsel {
    val fnrEllerOrgnr: String

    data class Sms(
        override val fnrEllerOrgnr: String,
        val mobilnummer: String,
        val tekst: String,
    ): EksternVarsel

    data class Epost(
        override val fnrEllerOrgnr: String,
        val epostadresse: String,
        val tittel: String,
        val tekst: String
    ): EksternVarsel
}

sealed interface EksternVarselTilstand {
    val varselId: UUID
    val notifikasjonId: UUID
    val produsentId: String

    val eksternVarsel: EksternVarsel

    data class Ny(
        override val varselId: UUID,
        override val eksternVarsel: EksternVarsel,
        override val notifikasjonId: UUID,
        override val produsentId: String,
    ) : EksternVarselTilstand

    data class Utført(
        override val varselId: UUID,
        override val eksternVarsel: EksternVarsel,
        override val notifikasjonId: UUID,
        override val produsentId: String,
        val response: AltinnVarselKlient.AltinnResponse
    ) : EksternVarselTilstand


    data class Kvittert(
        override val varselId: UUID,
        override val eksternVarsel: EksternVarsel,
        override val notifikasjonId: UUID,
        override val produsentId: String,
        val response: AltinnVarselKlient.AltinnResponse
    ) : EksternVarselTilstand
}

enum class EksterntVarselTilstand {
    NY, SENDT, KVITTERT
}

private val naisClientId = System.getenv("NAIS_CLIENT_ID") ?: "local:fager:notifikasjon-ekstern-varsling"

fun EksternVarselTilstand.Utført.toHendelse(): Hendelse =
    when (this.response) {
        is AltinnVarselKlient.AltinnResponse.Ok -> Hendelse.EksterntVarselVellykket(
            virksomhetsnummer = eksternVarsel.fnrEllerOrgnr,
            notifikasjonId = notifikasjonId,
            hendelseId = UUID.randomUUID(),
            produsentId = produsentId,
            kildeAppNavn = naisClientId,
            varselId = varselId,
            råRespons = response.rå
        )
        is AltinnVarselKlient.AltinnResponse.Feil -> Hendelse.EksterntVarselFeilet(
            virksomhetsnummer = eksternVarsel.fnrEllerOrgnr,
            notifikasjonId = notifikasjonId,
            hendelseId = UUID.randomUUID(),
            produsentId = produsentId,
            kildeAppNavn = naisClientId,
            varselId = varselId,
            råRespons = response.rå,
            altinnFeilkode = response.feilkode,
            feilmelding = response.feilmelding,
        )
    }
