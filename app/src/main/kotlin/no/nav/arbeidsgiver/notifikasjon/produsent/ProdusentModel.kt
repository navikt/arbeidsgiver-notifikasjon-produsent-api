package no.nav.arbeidsgiver.notifikasjon.produsent

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakStatus
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

object ProdusentModel {
    data class Sak(
        val id: UUID,
        val grupperingsid: String,
        val merkelapp: String,
        val virksomhetsnummer: String,
        val deletedAt: OffsetDateTime?,
        val tittel: String,
        val lenke: String,
        val statusoppdateringer: List<SakStatusOppdatering>,
        val mottakere: List<Mottaker>,
        val opprettetTidspunkt: OffsetDateTime,
    ) {
        fun statusoppdateringRegistrert() =
            statusoppdateringer.any { it.idempotencyKey.startsWith(IdempotenceKey.initial()) }
    }

    sealed interface Notifikasjon {
        val id: UUID
        val merkelapp: String
        val deletedAt: OffsetDateTime?
        val eksterneVarsler: List<EksterntVarsel>
        val virksomhetsnummer: String
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
        val mottakere: List<Mottaker>,
        val opprettetTidspunkt: OffsetDateTime,
        override val eksterneVarsler: List<EksterntVarsel>,
        override val virksomhetsnummer: String,
    ) : Notifikasjon {

        override fun erDuplikatAv(other: Notifikasjon): Boolean {
            return when (other) {
                is Beskjed -> {
                    this == other.copy(
                        opprettetTidspunkt = this.opprettetTidspunkt,
                        id = this.id,
                        eksterneVarsler = other.eksterneVarsler.mapIndexed { i, otherVarsel ->
                            val thisVarsel = this.eksterneVarsler.getOrNull(i) // simpler with getOrDefault?
                            if (thisVarsel == null) {
                                otherVarsel
                            } else {
                                otherVarsel.copy(
                                    varselId = thisVarsel.varselId,
                                    status = thisVarsel.status,
                                    feilmelding = thisVarsel.feilmelding,
                                    kildeHendelse = when (otherVarsel.kildeHendelse) {
                                        is AltinntjenesteVarselKontaktinfo -> otherVarsel.kildeHendelse.copy(varselId = thisVarsel.kildeHendelse?.varselId ?: otherVarsel.kildeHendelse.varselId)
                                        is EpostVarselKontaktinfo -> otherVarsel.kildeHendelse.copy(varselId = thisVarsel.kildeHendelse?.varselId ?: otherVarsel.kildeHendelse.varselId)
                                        is SmsVarselKontaktinfo -> otherVarsel.kildeHendelse.copy(varselId = thisVarsel.kildeHendelse?.varselId ?: otherVarsel.kildeHendelse.varselId)
                                    },
                                )
                            }
                        }
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
        val mottakere: List<Mottaker>,
        val opprettetTidspunkt: OffsetDateTime,
        val tilstand: Tilstand,
        override val eksterneVarsler: List<EksterntVarsel>,
        override val virksomhetsnummer: String,
        val frist: LocalDate?,
        val påminnelseEksterneVarsler: List<EksterntVarsel>,
    ) : Notifikasjon {

        enum class Tilstand {
            NY,
            UTFOERT,
            UTGAATT,
        }

        override fun erDuplikatAv(other: Notifikasjon): Boolean {
            return when (other) {
                is Oppgave -> {
                    this == other.copy(
                        opprettetTidspunkt = this.opprettetTidspunkt,
                        id = this.id,
                        eksterneVarsler = other.eksterneVarsler.mapIndexed { i, otherVarsel ->
                            val thisVarsel = this.eksterneVarsler.getOrNull(i) // simpler with getOrDefault?
                            if (thisVarsel == null) {
                                otherVarsel
                            } else {
                                otherVarsel.copy(
                                    varselId = thisVarsel.varselId,
                                    status = thisVarsel.status,
                                    feilmelding = thisVarsel.feilmelding,
                                    kildeHendelse = when (otherVarsel.kildeHendelse) {
                                        is AltinntjenesteVarselKontaktinfo -> otherVarsel.kildeHendelse.copy(varselId = thisVarsel.kildeHendelse?.varselId ?: otherVarsel.kildeHendelse.varselId)
                                        is EpostVarselKontaktinfo -> otherVarsel.kildeHendelse.copy(varselId = thisVarsel.kildeHendelse?.varselId ?: otherVarsel.kildeHendelse.varselId)
                                        is SmsVarselKontaktinfo -> otherVarsel.kildeHendelse.copy(varselId = thisVarsel.kildeHendelse?.varselId ?: otherVarsel.kildeHendelse.varselId)
                                    },
                                )
                            }
                        }
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
        val kildeHendelse: HendelseModel.EksterntVarsel
    ) {
        enum class Status {
            NY,
            SENDT,
            FEILET,
        }
    }

    data class SakStatusOppdatering(
        val id: UUID,
        val status: SakStatus,
        val overstyrStatustekstMed: String?,
        val tidspunktMottatt: OffsetDateTime,
        val tidspunktOppgitt: OffsetDateTime?,
        val idempotencyKey: String,
    )
}

fun BeskjedOpprettet.tilProdusentModel(): ProdusentModel.Beskjed =
    ProdusentModel.Beskjed(
        id = this.notifikasjonId,
        merkelapp = this.merkelapp,
        tekst = this.tekst,
        grupperingsid = this.grupperingsid,
        lenke = this.lenke,
        eksternId = this.eksternId,
        mottakere = this.mottakere,
        opprettetTidspunkt = this.opprettetTidspunkt,
        deletedAt = null,
        eksterneVarsler = eksterneVarsler.map(EksterntVarsel::tilProdusentModel),
        virksomhetsnummer = this.virksomhetsnummer,
    )

fun OppgaveOpprettet.tilProdusentModel(): ProdusentModel.Oppgave =
    ProdusentModel.Oppgave(
        id = this.notifikasjonId,
        merkelapp = this.merkelapp,
        tekst = this.tekst,
        grupperingsid = this.grupperingsid,
        lenke = this.lenke,
        eksternId = this.eksternId,
        mottakere = this.mottakere,
        opprettetTidspunkt = this.opprettetTidspunkt,
        tilstand = ProdusentModel.Oppgave.Tilstand.NY,
        deletedAt = null,
        eksterneVarsler = eksterneVarsler.map(EksterntVarsel::tilProdusentModel),
        virksomhetsnummer = this.virksomhetsnummer,
        frist = this.frist,
        påminnelseEksterneVarsler = this.påminnelse?.eksterneVarsler
            .orEmpty()
            .map(EksterntVarsel::tilProdusentModel),
    )

fun EksterntVarsel.tilProdusentModel(): ProdusentModel.EksterntVarsel {
    return when (this) {
        is SmsVarselKontaktinfo ->
            ProdusentModel.EksterntVarsel(
                varselId = this.varselId,
                status = ProdusentModel.EksterntVarsel.Status.NY,
                feilmelding = null,
                kildeHendelse = this,
            )
        is EpostVarselKontaktinfo ->
            ProdusentModel.EksterntVarsel(
                varselId = this.varselId,
                status = ProdusentModel.EksterntVarsel.Status.NY,
                feilmelding = null,
                kildeHendelse = this,
            )
        is AltinntjenesteVarselKontaktinfo ->
            ProdusentModel.EksterntVarsel(
                varselId = this.varselId,
                status = ProdusentModel.EksterntVarsel.Status.NY,
                feilmelding = null,
                kildeHendelse = this,
            )
    }
}
