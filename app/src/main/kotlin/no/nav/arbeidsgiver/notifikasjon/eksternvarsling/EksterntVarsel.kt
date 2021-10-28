package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

import com.fasterxml.jackson.databind.JsonNode
import java.util.*

sealed interface EksterntVarsel{
    val varselId: UUID

    data class Ny(
        override val varselId: UUID
    ) : EksterntVarsel

    data class Utført(
        override val varselId: UUID
    ) : EksterntVarsel

    data class Kvittert(
        override val varselId: UUID
    ) : EksterntVarsel
}

enum class EksterntVarselTilstand {
    NY, UTFØRT, KVITTERT
}

sealed interface AltinnResponse {
    val rå: JsonNode

    data class Ok(
        override val rå: JsonNode
    ) : AltinnResponse

    data class Feil(
        override val rå: JsonNode
    ) : AltinnResponse
}

