package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
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

        data class AltinnReportee(
            val virksomhet: String,
            val fnr: String,
        ) : Tilgang

        data class AltinnRolle(
            val virksomhet: String,
            val roleDefinitionId: String,
            val roleDefinitionCode: String,
        ) : Tilgang
    }

    sealed interface Notifikasjon {
        val id: UUID
        val virksomhetsnummer: String
        val sorteringTidspunkt: OffsetDateTime
    }

    data class Beskjed(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
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
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        override val virksomhetsnummer: String,
        val opprettetTidspunkt: OffsetDateTime,
        val utgaattTidspunkt: OffsetDateTime?,
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
        val statuser: List<SakStatus>,
        val oppgaver: List<OppgaveMetadata>,
    )

    data class SakStatus(
        val sakStatusId: UUID,
        val status: HendelseModel.SakStatus,
        val overstyrtStatustekst: String?,
        val tidspunkt: OffsetDateTime
    )

    data class Tilganger(
        val tjenestetilganger: List<Tilgang.Altinn> = listOf(),
        val reportee: List<Tilgang.AltinnReportee> = listOf(),
        val rolle: List<Tilgang.AltinnRolle> = listOf(),
        val harFeil: Boolean = false,
    ) {

        operator fun plus(other: Tilganger) = Tilganger(
            tjenestetilganger = this.tjenestetilganger + other.tjenestetilganger,
            reportee = this.reportee + other.reportee,
            rolle = this.rolle + other.rolle,
            harFeil = this.harFeil || other.harFeil,
        )

        companion object {
            val EMPTY = Tilganger()
            val FAILURE = Tilganger(harFeil = true)

            fun List<Tilganger>.flatten() = this.fold(EMPTY, Tilganger::plus)
        }
    }
}

