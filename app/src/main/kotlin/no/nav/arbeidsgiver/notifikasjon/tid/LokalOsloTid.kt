package no.nav.arbeidsgiver.notifikasjon.tid

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val norwayZoneId: ZoneId = ZoneId.of("Europe/Oslo")

object LokalOsloTid {

    fun now() : LocalDateTime = LocalDateTime.now(norwayZoneId)
}

fun LocalDateTime.atOslo() : ZonedDateTime = atZone(norwayZoneId)
