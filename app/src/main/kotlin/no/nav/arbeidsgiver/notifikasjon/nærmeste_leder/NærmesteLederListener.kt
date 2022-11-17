package no.nav.arbeidsgiver.notifikasjon.nærmeste_leder

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.JsonDeserializer
import java.time.LocalDate
import java.util.*

/**
 * Topic-ressurs: https://github.com/navikt/teamsykmelding-kafka-topics/blob/main/topics/narmesteleder/syfo-narmesteleder-leesah.yaml
 * Produsent-repo: https://github.com/navikt/narmesteleder
 * Eksempel konsument fra syfo-teamene: https://github.com/navikt/narmesteleder-varsel/blob/a8e03fbf14cc5e19dc77e169e3cabf2735a64922/src/main/kotlin/no/nav/syfo/narmesteleder/OppdaterNarmesteLederService.kt#L23
 */
interface NærmesteLederListener {
    suspend fun forEach(body: suspend (NarmesteLederLeesah) -> Unit)
}

class NarmesteLederLeesahDeserializer : JsonDeserializer<NarmesteLederLeesah>(NarmesteLederLeesah::class.java)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NarmesteLederLeesah(
    val narmesteLederId: UUID,
    val fnr: String,
    val narmesteLederFnr: String,
    val orgnummer: String,
    val aktivTom: LocalDate?,
//        utkommentert mtp dataminimering
//        val narmesteLederTelefonnummer: String,
//        val narmesteLederEpost: String,
//        val aktivFom: LocalDate,
//        val arbeidsgiverForskutterer: Boolean?,
//        val timestamp: OffsetDateTime
)