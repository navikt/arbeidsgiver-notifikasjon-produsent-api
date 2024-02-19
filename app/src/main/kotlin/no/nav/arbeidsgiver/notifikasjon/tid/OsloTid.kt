package no.nav.arbeidsgiver.notifikasjon.tid

import java.time.*

private val norwayZoneId: ZoneId = ZoneId.of("Europe/Oslo")

interface OsloTid {
    fun localDateTimeNow(): LocalDateTime
    fun localDateNow(): LocalDate
}

object OsloTidImpl : OsloTid {
    override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now(norwayZoneId)
    override fun localDateNow(): LocalDate = LocalDate.now(norwayZoneId)
}

fun LocalDateTime.atOslo():  ZonedDateTime = atZone(norwayZoneId)
fun LocalDateTime.atOsloAsOffsetDateTime():  OffsetDateTime = atOslo().toOffsetDateTime()

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

