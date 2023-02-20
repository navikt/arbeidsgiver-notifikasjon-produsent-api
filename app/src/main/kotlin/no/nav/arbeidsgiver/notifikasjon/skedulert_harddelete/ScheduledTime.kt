package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Instant

class ScheduledTime(
    private val spec: LocalDateTimeOrDuration,
    val baseTime: Instant,
) {
    fun happensAt(): Instant = when (spec) {
        is LocalDateTimeOrDuration.LocalDateTime -> spec.value.atOslo().toInstant()
        is LocalDateTimeOrDuration.Duration -> baseTime + spec.value
    }

    fun omOrNull() = spec.omOrNull()
    fun denOrNull() = spec.denOrNull()

    companion object {
        fun parse(spec: String, base: String) = ScheduledTime(
            spec = LocalDateTimeOrDuration.parse(spec),
            baseTime = Instant.parse(base),
        )
    }
}