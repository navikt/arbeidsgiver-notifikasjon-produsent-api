package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

object BrukerModel {
    sealed interface Tilgang {
        data class Altinn(
            val virksomhet: String,
            val servicecode: String,
            val serviceedition: String,
        ) : Tilgang
    }

    sealed interface Notifikasjon {
        val id: UUID
        val virksomhetsnummer: String
        val sorteringTidspunkt: OffsetDateTime
        val grupperingsid: String?
    }

    data class Beskjed(
        val merkelapp: String,
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
        val merkelapp: String,
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

    data class OppgaveMetadata(
        val tilstand: Oppgave.Tilstand,
        val frist: LocalDate?,
        val paaminnelseTidspunkt: OffsetDateTime?,
    )

    data class Sak(
        val sakId: UUID,
        val virksomhetsnummer: String,
        val tittel: String,
        val lenke: String,
        val merkelapp: String,
        val oppgaver: List<OppgaveMetadata>,
        val opprettetTidspunkt: Instant,
        val grupperingsid: String?,
    )

    data class Sakberikelse(
        val sisteStatus: SakStatus?,
        val tidslinje: List<TidslinjeElement>,
    )

    sealed interface TidslinjeElement {
        val id: UUID
        val grupperingsid: String
        val opprettetTidspunkt: Instant
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
        ): TidslinjeElement
        data class Beskjed(
            override val id: UUID,
            val tekst: String,
            override val grupperingsid: String,
            override val opprettetTidspunkt: Instant,
        ): TidslinjeElement
    }

    data class SakStatus(
        val status: HendelseModel.SakStatus,
        val overstyrtStatustekst: String?,
        val tidspunkt: OffsetDateTime
    )

    data class Tilganger(
        val tjenestetilganger: List<Tilgang.Altinn> = listOf(),
        val harFeil: Boolean = false,
    ) {

        operator fun plus(other: Tilganger) = Tilganger(
            tjenestetilganger = this.tjenestetilganger + other.tjenestetilganger,
            harFeil = this.harFeil || other.harFeil,
        )

        companion object {
            val EMPTY = Tilganger()
            val FAILURE = Tilganger(harFeil = true)

            fun List<Tilganger>.flatten() = this.fold(EMPTY, Tilganger::plus)
        }
    }
}

