package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
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
        val klikketPaa: Boolean
    ) : Notifikasjon

    data class Oppgave(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        override val virksomhetsnummer: String,
        val opprettetTidspunkt: OffsetDateTime,
        override val id: UUID,
        val klikketPaa: Boolean,
        val tilstand: Tilstand,
    ) : Notifikasjon {
        @Suppress("unused")
        /* leses fra database */
        enum class Tilstand {
            NY,
            UTFOERT
        }
    }

    data class Sak(
        val sakId: UUID,
        val virksomhetsnummer: String,
        val tittel: String,
        val lenke: String,
        val merkelapp: String,
        val statuser: List<SakStatus>,
    )

    data class SakStatus(
        val sakStatusId: UUID,
        val status: HendelseModel.SakStatus,
        val overstyrtStatustekst: String?,
        val tidspunkt: OffsetDateTime
    )

    data class Tilganger(
        val tjenestetilganger: List<BrukerModel.Tilgang.Altinn> = listOf(),
        val reportee: List<BrukerModel.Tilgang.AltinnReportee> = listOf(),
        val rolle: List<BrukerModel.Tilgang.AltinnRolle> = listOf(),
        val harFeil: Boolean = false,
    ) {

        operator fun plus(other: Tilganger) = Tilganger(
            tjenestetilganger = this.tjenestetilganger.plus(other.tjenestetilganger),
            reportee = this.reportee.plus(other.reportee),
            rolle = this.rolle.plus(other.rolle),
            harFeil = this.harFeil || other.harFeil,
        )

        companion object {
            val EMPTY = Tilganger()
            val FAILURE = Tilganger(harFeil = true)

            fun List<Tilganger>.flatten() = this.fold(EMPTY){ x, y -> x + y }
        }
    }
}

