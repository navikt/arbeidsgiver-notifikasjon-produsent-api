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
        val uuid: UUID,
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
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Mottaker

@JsonTypeName("fodselsnummer")
data class FodselsnummerMottaker (
    val fodselsnummer: String,
    val virksomhetsnummer: String
) : Mottaker()

@JsonTypeName("altinn")
data class AltinnMottaker(
    val altinntjenesteKode: String,
    val altinntjenesteVersjon: String,
    val virksomhetsnummer: String,
): Mottaker()
