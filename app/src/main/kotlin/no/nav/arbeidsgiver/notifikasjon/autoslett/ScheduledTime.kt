package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Instant
import java.time.OffsetDateTime

class ScheduledTime(
    private val spec: LocalDateTimeOrDuration,
    private val baseTime: OffsetDateTime
) {
    fun happensAt(): Instant = when (spec) {
        is LocalDateTimeOrDuration.LocalDateTime -> spec.value.atOslo().toInstant()
        is LocalDateTimeOrDuration.Duration -> (baseTime + spec.value).toInstant()
    }

    companion object {
        fun parse(spec: String, base: String) = ScheduledTime(
            spec = LocalDateTimeOrDuration.parse(spec),
            baseTime = OffsetDateTime.parse(base),
        )
    }
}