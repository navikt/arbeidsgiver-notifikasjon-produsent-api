package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import no.altinn.services.common.fault._2009._10.AltinnFault

sealed interface AltinnVarselKlientResponse {
    val rå: JsonNode
    data class Ok(
        override val rå: JsonNode
    ) : AltinnVarselKlientResponse, AltinnVarselKlientResponseOrException

    data class Feil(
        override val rå: JsonNode,
        val altinnFault: AltinnFault,
    ) : AltinnVarselKlientResponse, AltinnVarselKlientResponseOrException {
        fun isRetryable() = retryableErrorIds.contains(altinnFault.errorID)

        val feilkode: String get() = altinnFault.errorID.toString()
        val feilmelding: String get() = altinnFault.altinnErrorMessage.value

        override fun toString() = with(altinnFault) {
            """AltinnResponse.Feil(
                    altinnErrorMessage=${altinnErrorMessage.value}
                    altinnExtendedErrorMessage=${altinnExtendedErrorMessage.value}
                    altinnLocalizedErrorMessage=${altinnLocalizedErrorMessage.value}
                    errorGuid=${errorGuid.value}
                    errorID=${errorID}
                    userGuid=${userGuid.value}
                    userId=${userId.value}
                )
            """.trimIndent()
        }
    }
}

sealed interface AltinnVarselKlientResponseOrException

data class UkjentException(
    val exception: Throwable,
) : AltinnVarselKlientResponseOrException

/**
 * TAG-2054
 * https://altinn.github.io/docs/api/tjenesteeiere/soap/feilkoder/
 */
private val retryableErrorIds = listOf(
    0, // intern generell feil i altinn.
    44, // intern teknisk feil i altinn. ikke dokumentert
)
