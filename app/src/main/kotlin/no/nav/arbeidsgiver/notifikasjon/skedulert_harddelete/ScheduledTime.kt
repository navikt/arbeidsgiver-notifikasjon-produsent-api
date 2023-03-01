package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Instant
import java.time.ZoneOffset

class ScheduledTime(
    private val spec: LocalDateTimeOrDuration,
    val baseTime: Instant,
) {
    fun happensAt(): Instant = when (spec) {
        is LocalDateTimeOrDuration.LocalDateTime -> spec.value.atOslo().toInstant()
        is LocalDateTimeOrDuration.Duration ->
            /* adding years is not supported by Instant. */
            (baseTime.atOffset(ZoneOffset.UTC) + spec.value).toInstant()
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