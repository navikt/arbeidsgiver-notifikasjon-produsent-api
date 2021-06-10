package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.OffsetDateTime
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Hendelse {
    abstract val virksomhetsnummer: String

    @JsonTypeName("BeskjedOpprettet")
    data class BeskjedOpprettet(
        override val virksomhetsnummer: String,
        val merkelapp: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val id: UUID,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime
    ): Hendelse()

    @JsonTypeName("OppgaveOpprettet")
    data class OppgaveOpprettet(
        override val virksomhetsnummer: String,
        val merkelapp: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val id: UUID,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val opprettetTidspunkt: OffsetDateTime
    ): Hendelse()


    @JsonTypeName("BrukerKlikket")
    data class BrukerKlikket(
        override val virksomhetsnummer: String,
        val fnr: String,
        val notifikasjonsId: UUID
    ): Hendelse()

    @JsonTypeName("SlettHendelse")
    data class SlettHendelse(
        val notifikasjonsId: UUID,
        override val virksomhetsnummer: String
    ): Hendelse()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Mottaker

@JsonTypeName("naermesteLeder")
data class NærmesteLederMottaker (
    val naermesteLederFnr: String,
    val ansattFnr: String,
    val virksomhetsnummer: String
) : Mottaker()

@JsonTypeName("altinn")
data class AltinnMottaker(
    val serviceCode: String,
    val serviceEdition: String,
    val virksomhetsnummer: String,
): Mottaker()
