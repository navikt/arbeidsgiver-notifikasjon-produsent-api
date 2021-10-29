package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import java.util.*

sealed interface VarselFoo {
    data class Sms(
        val virksomhetsnummer: String,
        val mobilnummer: String,
        val tekst: String,
    ): VarselFoo

    data class Epost(
        val virksomhetsnummer: String,
        val epostadresse: String,
        val tittel: String,
        val tekst: String
    ): VarselFoo
}
sealed interface EksterntVarsel{
    val varselId: UUID

    val varselFoo: VarselFoo

    data class Ny(
        override val varselId: UUID,
        override val varselFoo: VarselFoo,
    ) : EksterntVarsel

    data class Utført(
        override val varselId: UUID,
        override val varselFoo: VarselFoo,
    ) : EksterntVarsel

    data class Kvittert(
        override val varselId: UUID,
        override val varselFoo: VarselFoo,
    ) : EksterntVarsel
}

enum class EksterntVarselTilstand {
    NY, UTFØRT, KVITTERT
}

