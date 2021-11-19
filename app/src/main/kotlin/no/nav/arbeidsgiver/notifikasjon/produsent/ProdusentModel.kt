package no.nav.arbeidsgiver.notifikasjon.produsent

import no.nav.arbeidsgiver.notifikasjon.*
import java.lang.IllegalArgumentException
import java.time.OffsetDateTime
import java.util.*

object ProdusentModel {
    sealed interface Notifikasjon {
        val id: UUID
        val merkelapp: String
        val deletedAt: OffsetDateTime?
        val eksterneVarsler: List<EksterntVarsel>
        fun erDuplikatAv(other: Notifikasjon): Boolean

        fun mergeEksterneVarsler(other: Notifikasjon): Notifikasjon {
            if (this.id != other.id) {
                throw IllegalArgumentException("merging requires same id")
            }
            return when (this) {
                is Beskjed -> {
                    copy(eksterneVarsler = eksterneVarsler + other.eksterneVarsler)
                }
                is Oppgave -> {
                    copy(eksterneVarsler = eksterneVarsler + other.eksterneVarsler)
                }
            }
        }
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
        override val eksterneVarsler: List<EksterntVarsel>,
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
        override val eksterneVarsler: List<EksterntVarsel>,
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

    data class EksterntVarsel(
        val varselId: UUID,
        val status: Status,
        val feilmelding: String?,
    ) {
        enum class Status {
            NY,
            SENDT,
            FEILET,
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
        eksterneVarsler = eksterneVarsler.map(EksterntVarsel::tilProdusentModel)
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
        eksterneVarsler = eksterneVarsler.map(EksterntVarsel::tilProdusentModel)
    )

fun EksterntVarsel.tilProdusentModel(): ProdusentModel.EksterntVarsel {
    return when (this) {
        is SmsVarselKontaktinfo ->
            ProdusentModel.EksterntVarsel(
                varselId = this.varselId,
                status = ProdusentModel.EksterntVarsel.Status.NY,
                feilmelding = null,
            )
        is EpostVarselKontaktinfo ->
            ProdusentModel.EksterntVarsel(
                varselId = this.varselId,
                status = ProdusentModel.EksterntVarsel.Status.NY,
                feilmelding = null,
            )
    }
}
