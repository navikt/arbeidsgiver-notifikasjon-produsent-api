package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Hendelse {
    /* Identifikator for denne hendelsen. */
    abstract val hendelseId: UUID

    /* Identifikator som grupperer hendelser sammen om en notifikasjon. */
    abstract val notifikasjonId: UUID

    abstract val virksomhetsnummer: String

    @JsonTypeName("BeskjedOpprettet")
    data class BeskjedOpprettet(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        val merkelapp: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime
    ) : Hendelse()

    @JsonTypeName("OppgaveOpprettet")
    data class OppgaveOpprettet(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        val merkelapp: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime
    ) : Hendelse()

    @JsonTypeName("OppgaveUtfoert")
    data class OppgaveUtført(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
    ) : Hendelse()

    @JsonTypeName("SoftDelete")
    data class SoftDelete(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        val deletedAt: OffsetDateTime,
    ) : Hendelse()

    @JsonTypeName("HardDelete")
    data class HardDelete(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        val deletedAt: OffsetDateTime,
    ) : Hendelse()

    @JsonTypeName("BrukerKlikket")
    data class BrukerKlikket(
        override val virksomhetsnummer: String,
        override val notifikasjonId: UUID,
        override val hendelseId: UUID,
        val fnr: String,
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



val Mottaker.virksomhetsnummer: String
    get() = when (this) {
        is NærmesteLederMottaker -> this.virksomhetsnummer
        is AltinnMottaker -> this.virksomhetsnummer
    }

data class HendelseMetadata(
    val timestamp: Instant
)