package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.SakStatus
import java.time.OffsetDateTime

data class SaksStatusInput(
    val status: SaksStatus,
    val tidspunkt: OffsetDateTime?,
    val overstyrStatustekstMed: String?,
)

enum class SaksStatus(val hendelseType: SakStatus) {
    MOTTATT(SakStatus.MOTTATT),
    UNDER_BEHANDLING(SakStatus.UNDER_BEHANDLING),
    FERDIG(SakStatus.FERDIG);
}


enum class IdempotencyPrefix(val serialized: String) {
    INITIAL("INITIAL"),
    USER_SUPPLIED("USER_SUPPLIED"),
    GENERATED("GENERATED")
}