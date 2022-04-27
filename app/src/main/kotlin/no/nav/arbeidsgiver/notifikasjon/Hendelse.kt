package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.JsonNode
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.requireGraphql
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

object HendelseModel {
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    sealed class Hendelse {
        /* ID-en til f.eks. en sak, oppgave eller beskjed som hendelsen handler om. */
        abstract val aggregateId: UUID

        /* Identifikator for denne hendelsen. */
        abstract val hendelseId: UUID

        abstract val virksomhetsnummer: String

        /* Identifikator for produsent slik som oppgitt i bruksvilkår */
        abstract val produsentId: String?

        //navn på app som har produsert hendelse
        abstract val kildeAppNavn: String

    }

    data class HendelseMetadata(
        val timestamp: Instant
    )

    interface Notifikasjon {
        val notifikasjonId: UUID
    }

    interface Sak {
        val sakId: UUID
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    sealed interface LocalDateTimeOrDuration {
        @JsonTypeName("LocalDateTime")
        data class LocalDateTime(val value: java.time.LocalDateTime): LocalDateTimeOrDuration
        @JsonTypeName("Duration")
        data class Duration(val value: java.time.Duration): LocalDateTimeOrDuration
    }

    @JsonTypeName("SakOpprettet")
    data class SakOpprettet
    @JsonIgnore constructor(
        override val hendelseId: UUID,
        override val virksomhetsnummer: String,
        override val produsentId: String,
        override val kildeAppNavn: String,
        override val sakId: UUID,

        val grupperingsid: String,
        val merkelapp: String,
        val mottakere: List<Mottaker>,
        val tittel: String,
        val lenke: String,
        val oppgittTidspunkt: OffsetDateTime?,
        val mottattTidspunkt: OffsetDateTime,
        val hardDelete: LocalDateTimeOrDuration?,
    ) : Hendelse(), Sak {
        @JsonIgnore
        override val aggregateId: UUID = sakId

        init {
            requireGraphql(mottakere.isNotEmpty()) {
                "minst 1 mottaker må gis"
            }
        }

        companion object {
            // Denne konstruktøren har default properties, og støtter historiske
            // JSON-felter man kan finne i kafka-topicen.
            // Denne konstruktøren skal ikke brukes i vår kode, fordi da er det lett å gå glipp
            // av å initialisere viktige felt.
            @JsonCreator
            @JvmStatic
            fun jsonConstructor(
                hendelseId: UUID,
                virksomhetsnummer: String,
                produsentId: String,
                kildeAppNavn: String,
                sakId: UUID,
                grupperingsid: String,
                merkelapp: String,
                mottakere: List<Mottaker>,
                tittel: String,
                lenke: String,

                /* I test-miljøet finnes events uten disse to propertiene. Men i prod, så har alle
                * begge properties satt. Så hvis vi rydder i dev-miljøet, så kan vi fjerne hele
                * custom-deserializeren. */
                oppgittTidspunkt: OffsetDateTime?,
                mottattTidspunkt: OffsetDateTime?,
                hardDelete: LocalDateTimeOrDuration?,
            ) = SakOpprettet(
                hendelseId = hendelseId,
                virksomhetsnummer = virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                sakId = sakId,
                grupperingsid = grupperingsid,
                merkelapp = merkelapp,
                mottakere = mottakere,
                tittel = tittel,
                lenke = lenke,
                oppgittTidspunkt = oppgittTidspunkt,
                mottattTidspunkt = mottattTidspunkt ?: OffsetDateTime.now(),
                hardDelete = hardDelete,
            )
        }
    }

    @JsonTypeName("NyStatusSak")
    data class NyStatusSak(
        override val hendelseId: UUID,
        override val virksomhetsnummer: String,
        override val produsentId: String,
        override val kildeAppNavn: String,
        override val sakId: UUID,

        val status: SakStatus,
        val overstyrStatustekstMed: String?,
        val oppgittTidspunkt: OffsetDateTime?,
        val mottattTidspunkt: OffsetDateTime,
        val idempotensKey: String,
        val hardDelete: HardDeleteUpdate?,
        val nyLenkeTilSak: String?,
    ) : Hendelse(), Sak {
        @JsonIgnore
        override val aggregateId: UUID = sakId
    }

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
        val hardDelete: LocalDateTimeOrDuration?,
    ) : Hendelse(), Notifikasjon {
        init {
            requireGraphql(mottakere.isNotEmpty()) {
                "minst 1 mottaker må gis"
            }
        }

        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId

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
                @JsonProperty("mottaker") mottaker: Mottaker? = null,
                mottakere: List<Mottaker> = listOf(),
                tekst: String,
                grupperingsid: String? = null,
                lenke: String,
                opprettetTidspunkt: OffsetDateTime,
                eksterneVarsler: List<EksterntVarsel> = listOf(),
                hardDelete: LocalDateTimeOrDuration?,
            ) = BeskjedOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                notifikasjonId = notifikasjonId,
                hendelseId = hendelseId,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = (listOfNotNull(mottaker) + mottakere),
                tekst = tekst,
                grupperingsid = grupperingsid,
                lenke = lenke,
                opprettetTidspunkt = opprettetTidspunkt,
                eksterneVarsler = eksterneVarsler,
                hardDelete = hardDelete,
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
        val hardDelete: LocalDateTimeOrDuration?,
    ) : Hendelse(), Notifikasjon {
        init {
            requireGraphql(mottakere.isNotEmpty()) {
                "minst 1 mottaker må gis"
            }
        }

        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId

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
                hardDelete: LocalDateTimeOrDuration?,
            ) = OppgaveOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                notifikasjonId = notifikasjonId,
                hendelseId = hendelseId,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                merkelapp = merkelapp,
                eksternId = eksternId,
                mottakere = (listOfNotNull(mottaker) + mottakere),
                tekst = tekst,
                grupperingsid = grupperingsid,
                lenke = lenke,
                opprettetTidspunkt = opprettetTidspunkt,
                eksterneVarsler = eksterneVarsler,
                hardDelete = hardDelete,
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
        val hardDelete: HardDeleteUpdate?,
    ) : Hendelse(), Notifikasjon {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    @JsonTypeName("SoftDelete")
    data class SoftDelete(
        override val virksomhetsnummer: String,
        @JsonProperty("notifikasjonId") override val aggregateId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val deletedAt: OffsetDateTime,
    ) : Hendelse()

    @JsonTypeName("HardDelete")
    data class HardDelete(
        override val virksomhetsnummer: String,
        @JsonProperty("notifikasjonId") override val aggregateId: UUID,
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
    ) : Hendelse(), Notifikasjon {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    @JsonTypeName("EksterntVarselVellykket")
    data class EksterntVarselVellykket(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val varselId: UUID,
        val råRespons: JsonNode,
    ) : Hendelse(), Notifikasjon {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

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
    ) : Hendelse(), Notifikasjon {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    sealed class Mottaker

    @JsonTypeName("altinnRolle")
    data class AltinnRolleMottaker(
        val roleDefinitionCode: String,
        val roleDefinitionId: String,
        val virksomhetsnummer: String
    ) : Mottaker()

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

    @JsonTypeName("altinnReportee")
    data class AltinnReporteeMottaker(
        val fnr: String,
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

    data class HardDeleteUpdate(
        val nyTid: LocalDateTimeOrDuration,
        val strategi: NyTidStrategi,
    )

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

    enum class SakStatus {
        MOTTATT,
        UNDER_BEHANDLING,
        FERDIG;
    }

    enum class NyTidStrategi {
        FORLENG,
        OVERSKRIV;
    }
}

val HendelseModel.Mottaker.virksomhetsnummer: String
    get() = when (this) {
        is HendelseModel.NærmesteLederMottaker -> this.virksomhetsnummer
        is HendelseModel.AltinnMottaker -> this.virksomhetsnummer
        is HendelseModel.AltinnReporteeMottaker -> this.virksomhetsnummer
        is HendelseModel.AltinnRolleMottaker -> this.virksomhetsnummer
    }
