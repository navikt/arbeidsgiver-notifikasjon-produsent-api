package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

object BrukerModel {
    sealed interface Tilgang {
        data class Altinn(
            val virksomhetsnummer: String,
            val tilgang: String,
        ) : Tilgang
    }

    sealed interface Notifikasjon {
        val id: UUID
        val virksomhetsnummer: String
        val sorteringTidspunkt: OffsetDateTime
        val merkelapp: String
        val grupperingsid: String?

        val gruppering: Gruppering?
            get() = grupperingsid?.let {
                Gruppering(
                    grupperingsid = it,
                    merkelapp = merkelapp,
                )
            }
    }

    data class Beskjed(
        override val merkelapp: String,
        val tekst: String,
        override val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        override val virksomhetsnummer: String,
        val opprettetTidspunkt: OffsetDateTime,
        override val id: UUID,
        val klikketPaa: Boolean,
    ) : Notifikasjon {
        override val sorteringTidspunkt: OffsetDateTime
            get() = opprettetTidspunkt
    }

    data class Oppgave(
        override val merkelapp: String,
        val tekst: String,
        override val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        override val virksomhetsnummer: String,
        val opprettetTidspunkt: OffsetDateTime,
        val utgaattTidspunkt: OffsetDateTime?,
        val utfoertTidspunkt: OffsetDateTime?,
        val paaminnelseTidspunkt: OffsetDateTime?,
        val frist: LocalDate?,
        override val id: UUID,
        val klikketPaa: Boolean,
        val tilstand: Tilstand,
    ) : Notifikasjon {
        enum class Tilstand {
            NY,
            UTFOERT,
            UTGAATT
        }

        override val sorteringTidspunkt: OffsetDateTime
            get() = paaminnelseTidspunkt ?: opprettetTidspunkt
    }

    data class Kalenderavtale(
        override val id: UUID,
        override val virksomhetsnummer: String,
        override val grupperingsid: String,
        override val merkelapp: String,

        val klikketPaa: Boolean,

        val tekst: String,
        val lenke: String,
        val eksternId: String,
        val tilstand: Tilstand,
        val opprettetTidspunkt: OffsetDateTime,
        val paaminnelseTidspunkt: OffsetDateTime?,
        val startTidspunkt: LocalDateTime,
        val sluttTidspunkt: LocalDateTime?,
        val lokasjon: Lokasjon?,
        val erDigitalt: Boolean?,
    ) : Notifikasjon {
        data class Lokasjon(
            val adresse: String,
            val postnummer: String,
            val poststed: String,
        )

        override val sorteringTidspunkt: OffsetDateTime
            get() = paaminnelseTidspunkt ?: opprettetTidspunkt

        enum class Tilstand {
            VENTER_SVAR_FRA_ARBEIDSGIVER,
            ARBEIDSGIVER_VIL_AVLYSE,
            ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
            ARBEIDSGIVER_HAR_GODTATT,
            AVLYST,
            AVHOLDT,
        }
    }

    data class Sak(
        val sakId: UUID,
        val virksomhetsnummer: String,
        val tittel: String,
        val lenke: String?,
        val nesteSteg: String?,
        val tilleggsinformasjon: String?,
        val merkelapp: String,
        val opprettetTidspunkt: Instant,
        val grupperingsid: String,
    ) {
        val gruppering: Gruppering
            get() = Gruppering(
                grupperingsid = grupperingsid,
                merkelapp = merkelapp,
            )
    }

    data class Gruppering(
        val grupperingsid: String,
        val merkelapp: String,
    )

    data class Sakberikelse(
        val sisteStatus: SakStatus?,
        val tidslinje: List<TidslinjeElement>,
    )

    sealed interface TidslinjeElement {
        val id: UUID
        val grupperingsid: String
        val opprettetTidspunkt: Instant
        val lenke: String
        data class Oppgave(
            override val id: UUID,
            val tekst: String,
            override val grupperingsid: String,
            override val opprettetTidspunkt: Instant,
            val tilstand: BrukerModel.Oppgave.Tilstand,
            val paaminnelseTidspunkt: Instant?,
            val utgaattTidspunkt: Instant?,
            val utfoertTidspunkt:  Instant?,
            val frist: LocalDate?,
            override val lenke: String
        ): TidslinjeElement

        data class Beskjed(
            override val id: UUID,
            val tekst: String,
            override val grupperingsid: String,
            override val opprettetTidspunkt: Instant,
            override val lenke: String
        ): TidslinjeElement
        data class Kalenderavtale(
            override val id: UUID,
            val tekst: String,
            override val grupperingsid: String,
            override val opprettetTidspunkt: Instant,
            val startTidspunkt: LocalDateTime,
            val sluttTidspunkt: LocalDateTime?,
            val avtaletilstand: BrukerModel.Kalenderavtale.Tilstand,
            val lokasjon: BrukerModel.Kalenderavtale.Lokasjon?,
            val digitalt: Boolean?,
            override val lenke: String
        ): TidslinjeElement
    }

    data class SakStatus(
        val status: HendelseModel.SakStatus,
        val overstyrtStatustekst: String?,
        val tidspunkt: OffsetDateTime
    )


    data class SakMetadata(
        val tittel: String,
        val tilleggsinformasjon: String?
    )
}

