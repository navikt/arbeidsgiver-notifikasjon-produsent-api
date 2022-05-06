package no.nav.arbeidsgiver.notifikasjon.tid

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.UnassignableInProduction
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val norwayZoneId: ZoneId = ZoneId.of("Europe/Oslo")

object LokalOsloTid {
    /* only change for test purposes */
    var clock: Clock by UnassignableInProduction { Clock.systemUTC() }

    fun now(): LocalDateTime =
        clock.instant().atZone(norwayZoneId).toLocalDateTime()
}

fun LocalDateTime.atOslo():  ZonedDateTime = atZone(norwayZoneId)

fun String.inOsloAsInstant(): Instant =
    LocalDateTime.parse(this).atOslo().toInstant()

