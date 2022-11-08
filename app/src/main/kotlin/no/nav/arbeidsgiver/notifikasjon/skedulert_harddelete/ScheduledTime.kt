package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Instant
import java.time.OffsetDateTime

class ScheduledTime(
    private val spec: LocalDateTimeOrDuration,
    val baseTime: OffsetDateTime
) {
    fun happensAt(): Instant = when (spec) {
        is LocalDateTimeOrDuration.LocalDateTime -> spec.value.atOslo().toInstant()
        is LocalDateTimeOrDuration.Duration -> (baseTime + spec.value).toInstant()
    }

    fun omOrNull() = spec.omOrNull()
    fun denOrNull() = spec.denOrNull()

    companion object {
        fun parse(spec: String, base: String) = ScheduledTime(
            spec = LocalDateTimeOrDuration.parse(spec),
            baseTime = OffsetDateTime.parse(base),
        )
    }
}