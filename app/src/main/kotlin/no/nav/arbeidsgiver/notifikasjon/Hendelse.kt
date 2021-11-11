package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
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
    data class BeskjedOpprettet(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val merkelapp: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime,

        // TODO: Det finnes hendelser i kafka-strømmen som mangler dette feltet.
        // Men det er dumt at vi må sette default value for hele prosjektet, siden
        // det kan være man glemmer å sette den, og på den måten får bugs.
        val eksterneVarsler: List<EksterntVarsel> = listOf()
    ) : Hendelse()

    @JsonTypeName("OppgaveOpprettet")
    data class OppgaveOpprettet(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val merkelapp: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime,

        // TODO: Det finnes hendelser i kafka-strømmen som mangler dette feltet.
        // Men det er dumt at vi må sette default value for hele prosjektet, siden
        // det kan være man glemmer å sette den, og på den måten får bugs.
        val eksterneVarsler: List<EksterntVarsel> = listOf()
    ) : Hendelse()

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