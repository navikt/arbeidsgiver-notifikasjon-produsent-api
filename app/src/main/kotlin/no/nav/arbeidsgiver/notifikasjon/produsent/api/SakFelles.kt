package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.SakStatus
import java.time.OffsetDateTime
import java.util.*

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


enum class IdempotenceKey(private val serialized: String) {
    INITIAL("INITIAL"),
    USER_SUPPLIED("USER_SUPPLIED"),
    GENERATED("GENERATED");

    companion object {
        fun initial() : String = INITIAL.serialized
        fun userSupplied(key: String) : String = USER_SUPPLIED.serialized + key
        fun generated(uuid: UUID) : String = GENERATED.serialized + uuid
    }
}