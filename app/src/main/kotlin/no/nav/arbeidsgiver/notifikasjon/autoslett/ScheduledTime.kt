package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel.LocalDateTimeOrDuration
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Instant

class ScheduledTime(
    private val spec: LocalDateTimeOrDuration,
    private val baseTime: Instant
) {

    fun happensAt(): Instant = when (spec) {
        is LocalDateTimeOrDuration.LocalDateTime -> spec.value.atOslo().toInstant()
        is LocalDateTimeOrDuration.Duration -> baseTime + spec.value
    }
}