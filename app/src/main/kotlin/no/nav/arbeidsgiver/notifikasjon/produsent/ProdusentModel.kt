package no.nav.arbeidsgiver.notifikasjon.produsent

import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import java.time.OffsetDateTime
import java.util.*

object ProdusentModel {
    sealed interface Notifikasjon {
        val id: UUID
        val merkelapp: String
        val deletedAt: OffsetDateTime?
        fun erDuplikatAv(other: Notifikasjon): Boolean
    }

    data class Beskjed(
        override val id: UUID,
        override val merkelapp: String,
        override val deletedAt: OffsetDateTime?,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
    ) : Notifikasjon {
        override fun erDuplikatAv(other: Notifikasjon): Boolean {
            return when (other) {
                is Beskjed -> {
                    this == other.copy(
                        opprettetTidspunkt = this.opprettetTidspunkt,
                        id = this.id
                    )
                }
                else -> false
            }
        }
    }

    data class Oppgave(
        override val id: UUID,
        override val merkelapp: String,
        override val deletedAt: OffsetDateTime?,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
        val tilstand: Tilstand,
    ) : Notifikasjon {

        @Suppress("unused")
        /* leses fra database */
        enum class Tilstand {
            NY,
            UTFOERT,
        }

        override fun erDuplikatAv(other: Notifikasjon): Boolean {
            return when (other) {
                is Oppgave -> {
                    this == other.copy(
                        opprettetTidspunkt = this.opprettetTidspunkt,
                        id = this.id
                    )
                }
                else -> false
            }
        }
    }

}

fun Hendelse.BeskjedOpprettet.tilProdusentModel(): ProdusentModel.Beskjed =
    ProdusentModel.Beskjed(
        id = this.notifikasjonId,
        merkelapp = this.merkelapp,
        tekst = this.tekst,
        grupperingsid = this.grupperingsid,
        lenke = this.lenke,
        eksternId = this.eksternId,
        mottaker = this.mottaker,
        opprettetTidspunkt = this.opprettetTidspunkt,
        deletedAt = null,
    )

fun Hendelse.OppgaveOpprettet.tilProdusentModel(): ProdusentModel.Oppgave =
    ProdusentModel.Oppgave(
        id = this.notifikasjonId,
        merkelapp = this.merkelapp,
        tekst = this.tekst,
        grupperingsid = this.grupperingsid,
        lenke = this.lenke,
        eksternId = this.eksternId,
        mottaker = this.mottaker,
        opprettetTidspunkt = this.opprettetTidspunkt,
        tilstand = ProdusentModel.Oppgave.Tilstand.NY,
        deletedAt = null,
    )