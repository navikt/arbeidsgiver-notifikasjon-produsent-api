package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import java.time.Duration
import java.time.LocalDateTime

data class FutureTemporalInput(
    val den: LocalDateTime?,
    val om: Duration?,
) {
    fun tilDomene(): HendelseModel.LocalDateTimeOrDuration {
        if (den != null) {
            return HendelseModel.LocalDateTimeOrDuration.LocalDateTime(den)
        }
        if (om != null) {
            return HendelseModel.LocalDateTimeOrDuration.Duration(om)
        }
        throw RuntimeException("Feil format")
    }
}