package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ISO8601PeriodTest : DescribeSpec({
    val examples = listOf(
        "P1DT",
        "PT1H",
        "P1DT1H",
        "P3Y6M4DT12H30M5S",
    )
    describe("ISO8601Period parse and print roundtrip") {
        forAll<String>(examples) { duration ->
            it("::parse and ::toString are inverse") {
                ISO8601Period.parse(duration).toString() shouldBe duration
            }
        }

        forAll<String>(examples.map { "\"$it\""}) { json ->
            it("jackson serde") {
                val obj = laxObjectMapper.readValue<ISO8601Period>(json)
                laxObjectMapper.writeValueAsString(obj) shouldBe json
            }
        }
    }
})
