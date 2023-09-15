package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
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

    data class Altinntjeneste(
        override val fnrEllerOrgnr: String,
        override val sendeVindu: EksterntVarselSendingsvindu,
        override val sendeTidspunkt: LocalDateTime?,
        val serviceCode: String,
        val serviceEdition: String,
        val tittel: String,
        val innhold: String
    ): EksternVarsel
}

data class EksternVarselStatiskData(
    val varselId: UUID,
    val notifikasjonId: UUID,
    val produsentId: String,
    val eksternVarsel: EksternVarsel,
)

sealed interface AltinnResponse {
    val rå: JsonNode

    data class Ok(
        override val rå: JsonNode
    ) : AltinnResponse

    data class Feil(
        override val rå: JsonNode,
        val feilkode: String,
        val feilmelding: String,
    ) : AltinnResponse
}

sealed interface EksternVarselTilstand {
    val data: EksternVarselStatiskData

    data class Ny(
        override val data: EksternVarselStatiskData
    ) : EksternVarselTilstand

    data class Sendt(
        override val data: EksternVarselStatiskData,
        val response: AltinnResponse
    ) : EksternVarselTilstand


    data class Kvittert(
        override val data: EksternVarselStatiskData,
        val response: AltinnResponse
    ) : EksternVarselTilstand

    fun kalkuertSendetidspunkt() =
        when (data.eksternVarsel.sendeVindu) {
            EksterntVarselSendingsvindu.NKS_ÅPNINGSTID -> Åpningstider.nesteNksÅpningstid()
            EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG -> Åpningstider.nesteDagtidIkkeSøndag()
            EksterntVarselSendingsvindu.LØPENDE -> OsloTid.localDateTimeNow()
            EksterntVarselSendingsvindu.SPESIFISERT -> data.eksternVarsel.sendeTidspunkt!!
        }

    fun asMDCContext() = MDCContext(mapOf(
        "varselId" to data.varselId.toString(),
        "aggregateId" to data.notifikasjonId.toString(),
        "produsentId" to data.produsentId,
    ))
}


enum class EksterntVarselTilstand {
    NY, SENDT, KVITTERT
}

enum class SendeStatus {
    OK, FEIL
}

private val naisClientId = System.getenv("NAIS_CLIENT_ID") ?: "local:fager:notifikasjon-ekstern-varsling"

fun EksternVarselTilstand.Sendt.toHendelse(): Hendelse =
    when (this.response) {
        is AltinnResponse.Ok -> EksterntVarselVellykket(
            virksomhetsnummer = data.eksternVarsel.fnrEllerOrgnr,
            notifikasjonId = data.notifikasjonId,
            hendelseId = UUID.randomUUID(),
            produsentId = data.produsentId,
            kildeAppNavn = naisClientId,
            varselId = data.varselId,
            råRespons = response.rå
        )
        is AltinnResponse.Feil -> EksterntVarselFeilet(
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
