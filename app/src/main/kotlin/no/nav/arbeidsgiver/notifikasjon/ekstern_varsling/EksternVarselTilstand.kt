package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import java.time.LocalDateTime
import java.util.*

sealed interface EksternVarsel {
    val fnrEllerOrgnr: String
    val sendeVindu: EksterntVarselSendingsvindu
    val sendeTidspunkt: LocalDateTime?

    data class Sms(
        override val fnrEllerOrgnr: String,
        override val sendeVindu: EksterntVarselSendingsvindu,
        override val sendeTidspunkt: LocalDateTime?,
        val mobilnummer: String,
        val tekst: String,
    ): EksternVarsel

    data class Epost(
        override val fnrEllerOrgnr: String,
        override val sendeVindu: EksterntVarselSendingsvindu,
        override val sendeTidspunkt: LocalDateTime?,
        val epostadresse: String,
        val tittel: String,
        val body: String
    ): EksternVarsel
}

data class EksternVarselStatiskData(
    val varselId: UUID,
    val notifikasjonId: UUID,
    val produsentId: String,
    val eksternVarsel: EksternVarsel,
)

sealed interface EksternVarselTilstand {
    val data: EksternVarselStatiskData

    data class Ny(
        override val data: EksternVarselStatiskData
    ) : EksternVarselTilstand

    data class Utført(
        override val data: EksternVarselStatiskData,
        val response: AltinnVarselKlient.AltinnResponse
    ) : EksternVarselTilstand


    data class Kvittert(
        override val data: EksternVarselStatiskData,
        val response: AltinnVarselKlient.AltinnResponse
    ) : EksternVarselTilstand
}

enum class EksterntVarselTilstand {
    NY, SENDT, KVITTERT
}

private val naisClientId = System.getenv("NAIS_CLIENT_ID") ?: "local:fager:notifikasjon-ekstern-varsling"

fun EksternVarselTilstand.Utført.toHendelse(): Hendelse =
    when (this.response) {
        is AltinnVarselKlient.AltinnResponse.Ok -> EksterntVarselVellykket(
            virksomhetsnummer = data.eksternVarsel.fnrEllerOrgnr,
            notifikasjonId = data.notifikasjonId,
            hendelseId = UUID.randomUUID(),
            produsentId = data.produsentId,
            kildeAppNavn = naisClientId,
            varselId = data.varselId,
            råRespons = response.rå
        )
        is AltinnVarselKlient.AltinnResponse.Feil -> EksterntVarselFeilet(
            virksomhetsnummer = data.eksternVarsel.fnrEllerOrgnr,
            notifikasjonId = data.notifikasjonId,
            hendelseId = UUID.randomUUID(),
            produsentId = data.produsentId,
            kildeAppNavn = naisClientId,
            varselId = data.varselId,
            råRespons = response.rå,
            altinnFeilkode = response.feilkode,
            feilmelding = response.feilmelding,
        )
    }
