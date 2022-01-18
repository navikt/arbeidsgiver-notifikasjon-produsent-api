package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Hendelse {
    /* Identifikator for denne hendelsen. */
    abstract val hendelseId: UUID

    /* Identifikator som grupperer hendelser sammen om en notifikasjon. */
    abstract val notifikasjonId: UUID

    abstract val virksomhetsnummer: String

    //Identifikator for produsent slik som oppgitt i bruksvilkår
    abstract val produsentId: String?

    //navn på app som har produsert hendelse
    abstract val kildeAppNavn: String

    @JsonTypeName("BeskjedOpprettet")
    data class BeskjedOpprettet
    @JsonIgnore constructor(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val merkelapp: String,
        val eksternId: String,
        val mottakere: List<Mottaker>,
        val tekst: String,
        val grupperingsid: String?,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime,
        val eksterneVarsler: List<EksterntVarsel>,
    ) : Hendelse() {

        @Deprecated("may be more than one!")
        val mottaker: Mottaker
            @JsonIgnore get() = mottakere[0]

        companion object {
            // Denne konstruktøren har default properties, og støtter historiske
            // JSON-felter man kan finne i kafka-topicen.
            // Denne konstruktøren skal ikke brukes i vår kode, fordi da er det lett å gå glipp
            // av å initialisere viktige felt.
            @JsonCreator
            @JvmStatic
            fun jsonConstructor(
                virksomhetsnummer: String,
                notifikasjonId: UUID,
                hendelseId: UUID,
                produsentId: String,
                kildeAppNavn: String,
                merkelapp: String,
                eksternId: String,
                mottaker: Mottaker? = null,
                mottakere: List<Mottaker> = listOf(),
                tekst: String,
                grupperingsid: String? = null,
                lenke: String,
                opprettetTidspunkt: OffsetDateTime,
                eksterneVarsler: List<EksterntVarsel> = listOf(),
            ) = BeskjedOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                notifikasjonId = notifikasjonId,
                hendelseId = hendelseId,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOfNotNull(mottaker) + mottakere,
                tekst = tekst,
                grupperingsid = grupperingsid,
                lenke = lenke,
                opprettetTidspunkt = opprettetTidspunkt,
                eksterneVarsler = eksterneVarsler
            )
        }
    }

    @JsonTypeName("OppgaveOpprettet")
    data class OppgaveOpprettet
        @JsonIgnore constructor(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val merkelapp: String,
        val eksternId: String,
        val mottakere: List<Mottaker>,
        val tekst: String,
        val grupperingsid: String?,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime,
        val eksterneVarsler: List<EksterntVarsel>,
    ) : Hendelse() {

        @Deprecated("may be more than one!")
        val mottaker: Mottaker
            @JsonIgnore get() = mottakere[0]

        companion object {
            @JvmStatic
            @JsonCreator
            fun jsonConstructor(
                virksomhetsnummer: String,
                notifikasjonId: UUID,
                hendelseId: UUID,
                produsentId: String,
                kildeAppNavn: String,
                merkelapp: String,
                eksternId: String,
                @JsonProperty("mottaker") mottaker: Mottaker? = null,
                mottakere: List<Mottaker> = listOf(),
                tekst: String,
                grupperingsid: String? = null,
                lenke: String,
                opprettetTidspunkt: OffsetDateTime,
                eksterneVarsler: List<EksterntVarsel> = listOf(),
            ) = OppgaveOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                notifikasjonId = notifikasjonId,
                hendelseId = hendelseId,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = listOfNotNull(mottaker) + mottakere,
                tekst = tekst,
                grupperingsid = grupperingsid,
                lenke = lenke,
                opprettetTidspunkt = opprettetTidspunkt,
                eksterneVarsler = eksterneVarsler,
            )
        }
    }

    @JsonTypeName("OppgaveUtfoert")
    data class OppgaveUtført(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
    ) : Hendelse()

    @JsonTypeName("SoftDelete")
    data class SoftDelete(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val deletedAt: OffsetDateTime,
    ) : Hendelse()

    @JsonTypeName("HardDelete")
    data class HardDelete(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val deletedAt: OffsetDateTime,
    ) : Hendelse()

    @JsonTypeName("BrukerKlikket")
    data class BrukerKlikket(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: Nothing? = null,
        override val kildeAppNavn: String,
        val fnr: String,
    ) : Hendelse()

    @JsonTypeName("EksterntVarselVellykket")
    data class EksterntVarselVellykket(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val varselId: UUID,
        val råRespons: JsonNode,
    ) : Hendelse()

    @JsonTypeName("EksterntVarselFeilet")
    data class EksterntVarselFeilet(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val varselId: UUID,
        val råRespons: JsonNode,
        val altinnFeilkode: String,
        val feilmelding: String,
    ) : Hendelse()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Mottaker

@JsonTypeName("naermesteLeder")
data class NærmesteLederMottaker(
    val naermesteLederFnr: String,
    val ansattFnr: String,
    val virksomhetsnummer: String
) : Mottaker()

@JsonTypeName("altinn")
data class AltinnMottaker(
    val serviceCode: String,
    val serviceEdition: String,
    val virksomhetsnummer: String,
) : Mottaker()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class EksterntVarsel {
    abstract val varselId: UUID
}

@JsonTypeName("smsVarselKontaktinfo")
data class SmsVarselKontaktinfo(
    override val varselId: UUID,
    val tlfnr: String,
    val fnrEllerOrgnr: String,
    val smsTekst: String,
    val sendevindu: EksterntVarselSendingsvindu,
    /* Kun gyldig hvis sendevindu er "SPESIFISERT" */
    val sendeTidspunkt: LocalDateTime?,
) : EksterntVarsel()

@JsonTypeName("epostVarselKontaktinfo")
data class EpostVarselKontaktinfo(
    override val varselId: UUID,
    val epostAddr: String,
    val fnrEllerOrgnr: String,
    val tittel: String,
    val htmlBody: String,
    val sendevindu: EksterntVarselSendingsvindu,
    /* Kun gyldig hvis sendevindu er "SPESIFISERT" */
    val sendeTidspunkt: LocalDateTime?,
) : EksterntVarsel()

enum class EksterntVarselSendingsvindu {
    /* Notifikasjonen sendes uten opphold fra vår side. Merk at underleverandører (Altinn) har eget vindu for utsendig
     * av SMS, og vi vil ikke overstyre det.
     **/
    LØPENDE,

    /* På dagtid mandag til lørdag, ikke søndag. Pr. nå 0800-1600. */
    DAGTID_IKKE_SØNDAG,

    /* Sendes så mottaker skal ha en reell mulighet for å kunne komme i kontakt med NKS/Arbeidsgivertelefonen.
     * Varsler sendes også litt før NKS åpner. Slutter å sende varsler litt før NKS lukker, så
     * mottaker har tid til å ringe. */
    NKS_ÅPNINGSTID,

    /* Varslingstidspunkt må spesifiseres i feltet "sendeTidspunkt". */
    SPESIFISERT,
}

val Mottaker.virksomhetsnummer: String
    get() = when (this) {
        is NærmesteLederMottaker -> this.virksomhetsnummer
        is AltinnMottaker -> this.virksomhetsnummer
    }

data class HendelseMetadata(
    val timestamp: Instant
)