package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel.LocalDateTimeOrDuration
import java.time.Instant
import java.time.ZoneId

class ScheduledTime(private val spec: LocalDateTimeOrDuration, private val baseTime: Instant) {

    fun happensAt(): Instant = when (spec) {
        is LocalDateTimeOrDuration.LocalDateTime -> spec.value.atZone(norwayZoneId).toInstant()
        is LocalDateTimeOrDuration.Duration -> baseTime + spec.value
    }

    companion object {
        private val norwayZoneId = ZoneId.of("Europe/Oslo")
    }
}