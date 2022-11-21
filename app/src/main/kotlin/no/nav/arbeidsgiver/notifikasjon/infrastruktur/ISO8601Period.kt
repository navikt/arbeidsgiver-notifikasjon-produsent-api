package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.Duration
import java.time.Period
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.time.temporal.TemporalUnit
import java.time.temporal.UnsupportedTemporalTypeException

data class ISO8601Period(
    private val period: Period,
    private val duration: Duration,
): TemporalAmount {

    @JsonValue
    override fun toString(): String {
        val datePart = if (period.isZero) "P" else period.toString()
        val timePart = if (duration.isZero) "" else duration.toString().trimStart('P')
        return "$datePart$timePart"
    }

    override fun get(unit: TemporalUnit): Long {
        if (unit in period.units) {
            return period.get(unit)
        }
        if (unit in duration.units) {
            return duration.get(unit)
        }
        throw UnsupportedTemporalTypeException("Unit not supported")
    }

    override fun getUnits(): List<TemporalUnit> = period.units + duration.units

    override fun addTo(temporal: Temporal): Temporal =
        duration.addTo(period.addTo(temporal))

    override fun subtractFrom(temporal: Temporal): Temporal =
        duration.subtractFrom(period.subtractFrom(temporal))

    companion object {
        private val format = Regex("""P([^T]*)(T(.*))?""")

        @JsonCreator
        @JvmStatic
        fun parse(text: String): ISO8601Period {
            val (datePart, _, timePart) = format.matchEntire(text)?.destructured
                ?: throw DateTimeParseException("Invalid ISO8601 duration", text, 0)
            return ISO8601Period(
                if (datePart == "") Period.ZERO else Period.parse("P${datePart}"),
                if (timePart == "") Duration.ZERO else Duration.parse("PT$timePart")
            )
        }
    }
}