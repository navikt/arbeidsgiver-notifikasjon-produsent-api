package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.tid.LokalOsloTid
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit.MINUTES

object Åpningstider {
    fun nesteNksÅpningstid(
        start: LocalDateTime = LokalOsloTid.now(),
    ): LocalDateTime = tidspunkterFremover(start).take(24 * 7).find(LocalDateTime::erNksÅpningstid)!!

    fun nesteDagtidIkkeSøndag(
        start: LocalDateTime = LokalOsloTid.now(),
    ): LocalDateTime = tidspunkterFremover(start).take(24 * 7).find(LocalDateTime::erDagtidIkkeSøndag)!!

    private fun tidspunkterFremover(
        start: LocalDateTime,
    ): Sequence<LocalDateTime> = generateSequence(start) { it.plusHours(1) }
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

