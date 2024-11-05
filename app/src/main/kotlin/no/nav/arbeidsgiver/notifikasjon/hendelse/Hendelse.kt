package no.nav.arbeidsgiver.notifikasjon.hendelse

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.JsonNode
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.requireGraphql
import no.nav.arbeidsgiver.notifikasjon.produsent.api.UgyldigPåminnelseTidspunktException
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import java.time.*
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
    ) {
        companion object {
            fun fromKafkaTimestamp(kafkaTimestamp: Long) =
                HendelseMetadata(Instant.ofEpochMilli(kafkaTimestamp))
        }
    }


    interface Notifikasjon {
        val notifikasjonId: UUID
    }

    interface Sak {
        val sakId: UUID
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    sealed interface LocalDateTimeOrDuration {

        companion object {
            fun parse(tekst: String) =
                if (tekst.startsWith("P")) Duration(ISO8601Period.parse(tekst))
                else LocalDateTime(java.time.LocalDateTime.parse(tekst))
        }

        @JsonTypeName("LocalDateTime")
        data class LocalDateTime(val value: java.time.LocalDateTime) : LocalDateTimeOrDuration {
            override fun toString() = value.toString()
        }

        @JsonTypeName("Duration")
        data class Duration(val value: ISO8601Period) : LocalDateTimeOrDuration {
            override fun toString() = value.toString()
        }

        fun omOrNull() = when (this) {
            is LocalDateTime -> null
            is Duration -> value
        }

        fun denOrNull() = when (this) {
            is LocalDateTime -> value
            is Duration -> null
        }
    }

    @JsonTypeName("Paaminnelse")
    data class Påminnelse(
        val tidspunkt: PåminnelseTidspunkt,
        val eksterneVarsler: List<EksterntVarsel>,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    sealed interface PåminnelseTidspunkt {
        companion object {
            fun createAndValidateKonkret(
                konkret: LocalDateTime,
                opprettetTidspunkt: OffsetDateTime,
                frist: LocalDate?,
                startTidspunkt: LocalDateTime?,
            ) =
                Konkret(konkret, konkret.inOsloAsInstant()).apply {
                    validerGrenseVerdier(opprettetTidspunkt, frist, startTidspunkt)
                }

            fun createAndValidateEtterOpprettelse(
                etterOpprettelse: ISO8601Period,
                opprettetTidspunkt: OffsetDateTime,
                frist: LocalDate?,
                startTidspunkt: LocalDateTime?,
            ) =
                EtterOpprettelse(etterOpprettelse, (opprettetTidspunkt + etterOpprettelse).toInstant()).apply {
                    validerGrenseVerdier(opprettetTidspunkt, frist, startTidspunkt)
                }

            fun createAndValidateFørFrist(
                førFrist: ISO8601Period,
                opprettetTidspunkt: OffsetDateTime,
                frist: LocalDate?
            ): FørFrist {
                if (frist == null) {
                    throw UgyldigPåminnelseTidspunktException("du må oppgi `frist`, siden `foerFrist` skal være relativ til denne")
                }

                return FørFrist(førFrist, (LocalDateTime.of(frist, LocalTime.MAX) - førFrist).inOsloAsInstant()).apply {
                    validerGrenseVerdier(opprettetTidspunkt, frist, null)
                }
            }

            fun createAndValidateFørStartTidspunkt(
                førStartTidpunkt: ISO8601Period,
                opprettetTidspunkt: OffsetDateTime,
                startTidspunkt: LocalDateTime?
            ): FørStartTidspunkt {
                if (startTidspunkt == null) {
                    throw UgyldigPåminnelseTidspunktException("foerStartTidspunkt er kun gyldig på kalenderavtaler")
                }

                return FørStartTidspunkt(førStartTidpunkt, (startTidspunkt - førStartTidpunkt).inOsloAsInstant()).apply {
                    validerGrenseVerdier(opprettetTidspunkt, null, startTidspunkt)
                }
            }

        }

        fun validerGrenseVerdier(opprettetTidspunkt: OffsetDateTime, frist: LocalDate?, startTidspunkt: LocalDateTime?) {
            if (påminnelseTidspunkt < opprettetTidspunkt.toInstant()) {
                throw UgyldigPåminnelseTidspunktException("påminnelsestidspunktet kan ikke være før oppgaven er opprettet")
            }
            if (frist != null && LocalDateTime.of(frist, LocalTime.MAX).inOsloAsInstant() < påminnelseTidspunkt) {
                throw UgyldigPåminnelseTidspunktException("påminnelsestidspunktet kan ikke være etter fristen på oppgaven")
            }
            if (startTidspunkt != null && startTidspunkt.inOsloAsInstant() < påminnelseTidspunkt) {
                throw UgyldigPåminnelseTidspunktException("påminnelsestidspunktet kan ikke være etter startTidspunkt på kalenderavtalen")
            }
        }

        val påminnelseTidspunkt: Instant

        @JsonTypeName("PaaminnelseTidspunkt.Konkret")
        data class Konkret(
            @JsonProperty("konkret")
            val konkret: LocalDateTime,
            @JsonProperty("paaminnelseTidspunkt")
            override val påminnelseTidspunkt: Instant,
        ) : PåminnelseTidspunkt

        @JsonTypeName("PaaminnelseTidspunkt.EtterOpprettelse")
        data class EtterOpprettelse(
            @JsonProperty("etterOpprettelse")
            val etterOpprettelse: ISO8601Period,
            @JsonProperty("paaminnelseTidspunkt")
            override val påminnelseTidspunkt: Instant,
        ) : PåminnelseTidspunkt

        @JsonTypeName("PaaminnelseTidspunkt.FoerFrist")
        data class FørFrist(
            @JsonProperty("foerFrist")
            val førFrist: ISO8601Period,
            @JsonProperty("paaminnelseTidspunkt")
            override val påminnelseTidspunkt: Instant,
        ) : PåminnelseTidspunkt

        @JsonTypeName("PaaminnelseTidspunkt.FoerStartTidspunkt")
        data class FørStartTidspunkt(
            @JsonProperty("foerStartTidspunkt")
            val førStartTidpunkt: ISO8601Period,
            @JsonProperty("paaminnelseTidspunkt")
            override val påminnelseTidspunkt: Instant,
        ) : PåminnelseTidspunkt
    }

    @JsonTypeName("PaaminnelseOpprettet")
    data class PåminnelseOpprettet
    @JsonIgnore constructor (
        override val virksomhetsnummer: String,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val notifikasjonId: UUID,
        val bestillingHendelseId: UUID,
        val opprettetTidpunkt: Instant,
        val fristOpprettetTidspunkt: Instant,
        val frist: LocalDate?,
        val tidspunkt: PåminnelseTidspunkt,
        val eksterneVarsler: List<EksterntVarsel>,
    ) : Hendelse() {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId

        companion object {
            @JvmStatic
            @JsonCreator
            fun jsonConstructor(
                virksomhetsnummer: String,
                hendelseId: UUID,
                produsentId: String,
                kildeAppNavn: String,
                notifikasjonId: UUID,
                bestillingHendelseId: UUID?,
                opprettetTidpunkt: Instant,
                /** `oppgaveOpprettetTidspunkt` is the old name, replaced by `fristOpprettetTidspunkt`. */
                oppgaveOpprettetTidspunkt: Instant?,
                fristOpprettetTidspunkt: Instant?,
                frist: LocalDate?,
                tidspunkt: PåminnelseTidspunkt,
                eksterneVarsler: List<EksterntVarsel>,
            ) = PåminnelseOpprettet(
                virksomhetsnummer = virksomhetsnummer,
                hendelseId = hendelseId,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                notifikasjonId = notifikasjonId,

                /** Historisk sett, så var alle bestillinger knyttet til `OppgaveOpprettet`-hendelsen og
                 * dette feltet fantest derfor ikke. Nå er det mulig at bestillingen også kommer fra en
                 * `FristUtsatt`-hendelse.
                 *
                 * I dag blir `bestillingsHendelseId` alltid satt når denne hendelsen produseres, men for
                 * historiske data, blir det riktig å sette bestillingHendelseId til notifikasjonId-en, siden de
                 * alltid kom fra `OppgaveOpprettet`, hvor hendelseId og notifikasjonId er det samme.
                 */
                bestillingHendelseId = bestillingHendelseId ?: notifikasjonId,

                opprettetTidpunkt = opprettetTidpunkt,
                fristOpprettetTidspunkt = checkNotNull(fristOpprettetTidspunkt ?: oppgaveOpprettetTidspunkt) {
                    "Missing both fristOpprettetTidspunkt and oppgaveOpprettetTidspunkt for PåminnelseOpprettet(notifikasjonId=$notifikasjonId, hendelseId=$hendelseId)"
                },
                frist = frist,
                tidspunkt = tidspunkt,
                eksterneVarsler = eksterneVarsler,
            )
        }
    }

    @JsonTypeName("SakOpprettet")
    data class SakOpprettet(
        override val hendelseId: UUID,
        override val virksomhetsnummer: String,
        override val produsentId: String,
        override val kildeAppNavn: String,
        override val sakId: UUID,

        val grupperingsid: String,
        val merkelapp: String,
        val mottakere: List<Mottaker>,
        val tittel: String,
        val tilleggsinformasjon: String?,
        val lenke: String?,
        val oppgittTidspunkt: OffsetDateTime?,
        val mottattTidspunkt: OffsetDateTime?,
        val nesteSteg: String?,
        val hardDelete: LocalDateTimeOrDuration?,
    ) : Hendelse(), Sak {
        @JsonIgnore
        override val aggregateId: UUID = sakId

        fun opprettetTidspunkt(fallback: Instant): Instant =
            (oppgittTidspunkt ?: mottattTidspunkt)?.toInstant() ?: fallback

        fun opprettetTidspunkt(fallback: OffsetDateTime): OffsetDateTime =
            oppgittTidspunkt ?: mottattTidspunkt ?: fallback

        init {
            requireGraphql(mottakere.isNotEmpty()) {
                "minst 1 mottaker må gis"
            }
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

        @get:JsonIgnore
        val opprettetTidspunkt: OffsetDateTime
            get() = oppgittTidspunkt ?: mottattTidspunkt
    }

    @JsonTypeName("TilleggsinformasjonSak")
    data class TilleggsinformasjonSak(
        override val hendelseId: UUID,
        override val virksomhetsnummer: String,
        override val produsentId: String,
        override val kildeAppNavn: String,
        override val sakId: UUID,

        val merkelapp: String,
        val grupperingsid: String,
        val idempotenceKey: String?,
        val tilleggsinformasjon: String?,
    ) : Hendelse(), Sak {
        @JsonIgnore
        override val aggregateId: UUID = sakId
    }

    @JsonTypeName("NesteStegSak")
    data class NesteStegSak(
        override val hendelseId: UUID,
        override val virksomhetsnummer: String,
        override val produsentId: String,
        override val kildeAppNavn: String,
        override val sakId: UUID,

        val merkelapp: String,
        val grupperingsid: String,
        val idempotenceKey: String?,
        val nesteSteg: String?,
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
        val sakId: UUID?,
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
                sakId: UUID?,
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
                sakId = sakId,
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
        val frist: LocalDate?,
        val påminnelse: Påminnelse?,
        val sakId: UUID?,
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
                frist: LocalDate? = null,
                påminnelse: Påminnelse? = null,
                sakId: UUID?,
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
                frist = frist,
                påminnelse = påminnelse,
                sakId = sakId,
            )
        }
    }

    @JsonTypeName("KalenderavtaleOpprettet")
    data class KalenderavtaleOpprettet(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val merkelapp: String,
        val grupperingsid: String,
        val eksternId: String,
        val mottakere: List<Mottaker>,
        val hardDelete: LocalDateTimeOrDuration?,
        val sakId: UUID,
        val lenke: String,
        val tekst: String,
        val opprettetTidspunkt: OffsetDateTime,
        val tilstand: KalenderavtaleTilstand,
        val startTidspunkt: LocalDateTime,
        val sluttTidspunkt: LocalDateTime?,
        val lokasjon: Lokasjon?,
        val erDigitalt: Boolean,
        val eksterneVarsler: List<EksterntVarsel>,
        val påminnelse: Påminnelse?,
    ) : Hendelse(), Notifikasjon {
        init {
            requireGraphql(mottakere.isNotEmpty()) {
                "minst 1 mottaker må gis"
            }
        }

        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    @JsonTypeName("KalenderavtaleOppdatert")
    data class KalenderavtaleOppdatert(
        override val virksomhetsnummer: String,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val notifikasjonId: UUID,
        val merkelapp: String,
        val grupperingsid: String,
        val opprettetTidspunkt: Instant,
        val oppdatertTidspunkt: Instant,
        val tilstand: KalenderavtaleTilstand?,
        val lenke: String?,
        val tekst: String?,
        val startTidspunkt: LocalDateTime?,
        val sluttTidspunkt: LocalDateTime?,
        val lokasjon: Lokasjon?,
        val erDigitalt: Boolean?,
        val hardDelete: HardDeleteUpdate?,
        val eksterneVarsler: List<EksterntVarsel>,
        val påminnelse: Påminnelse?,
        val idempotenceKey: String?,
    ) : Hendelse() {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    enum class KalenderavtaleTilstand {
        VENTER_SVAR_FRA_ARBEIDSGIVER,
        ARBEIDSGIVER_VIL_AVLYSE,
        ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
        ARBEIDSGIVER_HAR_GODTATT,
        AVLYST,
    }

    @JsonTypeName("Lokasjon")
    data class Lokasjon(
        val adresse: String,
        val postnummer: String,
        val poststed: String,
    )

    @JsonTypeName("FristUtsatt")
    data class FristUtsatt(
        override val virksomhetsnummer: String,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val notifikasjonId: UUID,
        val merkelapp: String,
        val fristEndretTidspunkt: Instant,
        val frist: LocalDate,
        val påminnelse: Påminnelse?,
    ) : Hendelse() {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    @JsonTypeName("OppgaveUtgaatt")
    data class OppgaveUtgått(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val hardDelete: HardDeleteUpdate?,
        val utgaattTidspunkt: OffsetDateTime,
        val nyLenke: String?,
    ) : Hendelse(), Notifikasjon {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    @JsonTypeName("OppgaveUtfoert")
    data class OppgaveUtført(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val hardDelete: HardDeleteUpdate?,
        val nyLenke: String?,
        val utfoertTidspunkt: OffsetDateTime?, //Vi har ikke utfoertTidspunkt på tidligere hendelser. Hentes fra metadata til kafka-eventet.
    ) : Hendelse(), Notifikasjon {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
    }

    @JsonTypeName("OppgavePaaminnelseEndret")
    data class OppgavePåminnelseEndret(
        override val virksomhetsnummer: String,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val notifikasjonId: UUID,
        val merkelapp: String,
        val frist: LocalDate?,
        val oppgaveOpprettetTidspunkt: Instant,
        val påminnelse: Påminnelse?,
        val idempotenceKey: String?,
    ) : Hendelse(){
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
        /** Brukes bare for å kunne se i hendelses-loggen når slettingen utført. */
        val deletedAt: OffsetDateTime,
        val grupperingsid: String?,
        val merkelapp: String?,
    ) : Hendelse()

    @JsonTypeName("HardDelete")
    data class HardDelete(
        override val virksomhetsnummer: String,
        @JsonProperty("notifikasjonId") override val aggregateId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        /** Brukes bare for å kunne se i hendelses-loggen når slettingen utført. */
        val deletedAt: OffsetDateTime,
        val grupperingsid: String?,
        val merkelapp: String?,
    ) : Hendelse() {
        @JsonIgnore
        val erSak = grupperingsid != null
    }

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

    @JsonTypeName("EksterntVarselKansellert")
    data class EksterntVarselKansellert(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        override val produsentId: String,
        override val kildeAppNavn: String,
        val varselId: UUID,
    ) : Hendelse(), Notifikasjon {
        @JsonIgnore
        override val aggregateId: UUID = notifikasjonId
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

    @JsonTypeName("altinntjenesteVarselKontaktinfo")
    data class AltinntjenesteVarselKontaktinfo(
        override val varselId: UUID,
        val serviceCode: String,
        val serviceEdition: String,
        val virksomhetsnummer: String,
        val tittel: String,
        val innhold: String,
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
    }
