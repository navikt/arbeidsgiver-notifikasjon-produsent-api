package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit.MINUTES

class LokalOsloTid {
    companion object {
        private val norwayZoneId = ZoneId.of("Europe/Oslo")
        fun nå(): LocalDateTime = LocalDateTime.now(norwayZoneId).truncatedTo(MINUTES)
        fun nesteNksÅpningstid(
            start: LocalDateTime = nå(),
        ): LocalDateTime = tidspunkterFremover(start).take(24 * 7).find(LocalDateTime::erNksÅpningstid)!!

        fun nesteDagtidIkkeSøndag(
            start: LocalDateTime = nå(),
        ): LocalDateTime = tidspunkterFremover(start).take(24 * 7).find(LocalDateTime::erDagtidIkkeSøndag)!!

        private fun tidspunkterFremover(
            start: LocalDateTime = nå(),
        ): Sequence<LocalDateTime> = generateSequence(start) { it.plusHours(1) }
    }
}

fun LocalDateTime.erNksÅpningstid(): Boolean =
    dayOfWeek != SATURDAY &&
            dayOfWeek != SUNDAY &&
            toLocalTime().isBetween(LocalTime.of(8, 30), LocalTime.of(14, 30))

fun LocalDateTime.erDagtidIkkeSøndag(): Boolean =
    dayOfWeek != SUNDAY && toLocalTime().isBetween(LocalTime.of(9, 0), LocalTime.of(16, 0))

fun LocalTime.isBetween(beginning: LocalTime, end: LocalTime): Boolean {
    val time = truncatedTo(MINUTES)
    return beginning <= time && time <= end
}

