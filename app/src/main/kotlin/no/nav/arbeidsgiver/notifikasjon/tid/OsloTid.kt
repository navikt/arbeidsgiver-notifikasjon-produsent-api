package no.nav.arbeidsgiver.notifikasjon.tid

import java.time.*

private val norwayZoneId: ZoneId = ZoneId.of("Europe/Oslo")

object OsloTid {
    fun localDateTimeNow(): LocalDateTime = LocalDateTime.now(norwayZoneId)

    fun localDateNow(): LocalDate =
        LocalDate.now(norwayZoneId)
}

fun LocalDateTime.atOslo():  ZonedDateTime = atZone(norwayZoneId)

fun Instant.asOsloLocalDateTime(): LocalDateTime =
    this.atZone(norwayZoneId).toLocalDateTime()

fun Instant.asOsloLocalDate(): LocalDate =
    this.atZone(norwayZoneId).toLocalDate()

fun OffsetDateTime.asOsloLocalDate(): LocalDate =
    this.toInstant().asOsloLocalDate()

fun String.inOsloAsInstant(): Instant =
    LocalDateTime.parse(this).inOsloAsInstant()

fun LocalDateTime.inOsloAsInstant(): Instant =
    this.atOslo().toInstant()

fun OffsetDateTime.inOsloLocalDateTime() = toInstant().asOsloLocalDateTime()

