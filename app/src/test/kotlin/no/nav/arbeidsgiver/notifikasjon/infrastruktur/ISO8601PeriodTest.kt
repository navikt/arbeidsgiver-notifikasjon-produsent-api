package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ISO8601PeriodTest {
    val simpleExamples = listOf(
        "P",
        "P1D",
        "PT1H",
        "P1DT1H",
        "P2Y",
        "P3Y6M4DT12H30M5S",
    )
    val complexExamples = listOf(
        "PT" to "P",
        "P1DT" to "P1D",
        "P1YT" to "P1Y",
        "P2YT" to "P2Y",
    )
    val examples = simpleExamples.map { it to it } + complexExamples

    @Test
    fun `parse - toString`() {
        examples.forEach { (input, expected) ->
            assertEquals(expected, ISO8601Period.parse(input).toString())
        }
    }

    @Test
    fun `jackson serde`() {
        examples.forEach { (input, expected) ->
            val obj = laxObjectMapper.readValue<ISO8601Period>("\"$input\"")
            assertEquals("\"$expected\"", laxObjectMapper.writeValueAsString(obj))
        }
    }

    @Test
    fun `toString+parse preserve equality`() {
        examples.forEach { (input, _) ->
            assertEquals(ISO8601Period.parse(ISO8601Period.parse(input).toString()), ISO8601Period.parse(input))
        }
    }

    @Test
    fun `håndterer skuddår`() {
        // fra 29. feb (skuddår) frem ett år
        var start = LocalDateTime.parse("2020-02-29T12:00")
        var step = ISO8601Period.parse("P1YT")
        var expected = LocalDateTime.parse("2021-02-28T12:00")
        assertEquals(expected, start + step)

        // fra 20. feb (skuddår) frem 1 måned
        start = LocalDateTime.parse("2020-02-20T12:00")
        step = ISO8601Period.parse("P1MT")
        expected = LocalDateTime.parse("2020-03-20T12:00")
        assertEquals(expected, start + step)

        // fra 20. feb (skuddår) frem 1 år
        start = LocalDateTime.parse("2020-02-20T12:00")
        step = ISO8601Period.parse("P1YT")
        expected = LocalDateTime.parse("2021-02-20T12:00")
        assertEquals(expected, start + step)

        // fra 28. feb 23:00 (året før skuddår) frem 1 år og 2 timer
        start = LocalDateTime.parse("2019-02-28T23:00")
        step = ISO8601Period.parse("P1YT2H")
        expected = LocalDateTime.parse("2020-02-29T01:00")
        assertEquals(expected, start + step)
    }
}
