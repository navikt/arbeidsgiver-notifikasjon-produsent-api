package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import java.time.LocalDateTime

class ISO8601PeriodTest : DescribeSpec({
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

    describe("ISO8601Period parse and print roundtrip") {
        withData(examples) { (input, expected) ->
            it("$input ::parse -> ::toString is $expected") {
                ISO8601Period.parse(input).toString() shouldBe expected
            }

            it("jackson serde $input -> $expected") {
                val obj = laxObjectMapper.readValue<ISO8601Period>("\"$input\"")
                laxObjectMapper.writeValueAsString(obj) shouldBe "\"$expected\""
            }

            it("toString+parse preserve equality:  $input") {
                ISO8601Period.parse(input) shouldBe ISO8601Period.parse(ISO8601Period.parse(input).toString())
            }
        }
    }

    describe("håndterer skuddår") {
        it("fra 29. feb (skuddår) frem ett år") {
            val start = LocalDateTime.parse("2020-02-29T12:00")
            val step = ISO8601Period.parse("P1YT")
            val expected = LocalDateTime.parse("2021-02-28T12:00")
            start + step shouldBe expected
        }

        it("fra 20. feb (skuddår) frem 1 måned") {
            val start = LocalDateTime.parse("2020-02-20T12:00")
            val step = ISO8601Period.parse("P1MT")
            val expected = LocalDateTime.parse("2020-03-20T12:00")
            start + step shouldBe expected
        }

        it("fra 20. feb (skuddår) frem 1 år") {
            val start = LocalDateTime.parse("2020-02-20T12:00")
            val step = ISO8601Period.parse("P1YT")
            val expected = LocalDateTime.parse("2021-02-20T12:00")
            start + step shouldBe expected
        }

        it("fra 28. feb 23:00 (året før skuddår) frem 1 år og 2 timer") {
            val start = LocalDateTime.parse("2019-02-28T23:00")
            val step = ISO8601Period.parse("P1YT2H")
            val expected = LocalDateTime.parse("2020-02-29T01:00")
            start + step shouldBe expected
        }
    }
})
