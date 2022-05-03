package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.tid.LokalOsloTid
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit.MINUTES

interface Åpningstider {
    fun nå(): LocalDateTime
    fun nesteNksÅpningstid(): LocalDateTime
    fun nesteDagtidIkkeSøndag(): LocalDateTime
}

object ÅpningstiderImpl : Åpningstider {
    override fun nå(): LocalDateTime = LokalOsloTid.now().truncatedTo(MINUTES)
    override fun nesteDagtidIkkeSøndag(): LocalDateTime = nesteDagtidIkkeSøndag(nå())
    override fun nesteNksÅpningstid(): LocalDateTime = nesteNksÅpningstid(nå())

    internal fun nesteNksÅpningstid(
        start: LocalDateTime,
    ): LocalDateTime = tidspunkterFremover(start).take(24 * 7).find(LocalDateTime::erNksÅpningstid)!!

    internal fun nesteDagtidIkkeSøndag(
        start: LocalDateTime = nå(),
    ): LocalDateTime = tidspunkterFremover(start).take(24 * 7).find(LocalDateTime::erDagtidIkkeSøndag)!!

    private fun tidspunkterFremover(
        start: LocalDateTime = nå(),
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

