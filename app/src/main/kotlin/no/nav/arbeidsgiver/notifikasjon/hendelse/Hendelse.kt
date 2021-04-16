package no.nav.arbeidsgiver.notifikasjon.hendelse

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Event {
    abstract val merkelapp: String
    abstract val eksternId: String
    abstract val mottaker: Mottaker
}

@JsonTypeName("beskjed opprettet")
data class BeskjedOpprettet(
    override val merkelapp: String,
    override val eksternId: String,
    override val mottaker: Mottaker,

    /* nb. id-en er kun ment for å identifisere eventet */
    val guid: UUID,
    val tekst: String,
    val grupperingsid: String? = null,
    val lenke: String,
    val opprettetTidspunkt: OffsetDateTime
): Event()

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
