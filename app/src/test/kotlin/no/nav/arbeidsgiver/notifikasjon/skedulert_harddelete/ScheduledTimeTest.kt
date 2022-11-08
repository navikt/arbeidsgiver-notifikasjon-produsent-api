package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class ScheduledTimeTest : DescribeSpec({
    describe("ScheduledTimeTest#happensAt") {
        it("spec med absolutt tidspunkt") {
            val sched = ScheduledTime.parse(spec = "2022-04-01T12:30", base = "2022-04-01T12:30:00.00Z")
            sched.happensAt() shouldBe Instant.parse("2022-04-01T10:30:00.00Z")
        }

        it("spec med timer-duration") {
            val sched = ScheduledTime.parse(spec = "PT5H", base = "2022-04-01T12:30:00.00Z")
            sched.happensAt() shouldBe Instant.parse("2022-04-01T17:30:00.00Z")
        }

        it("spec med år-duration") {
            val sched = ScheduledTime.parse(spec = "P1YT5H", base = "2022-04-01T12:30:00.00Z")
            sched.happensAt() shouldBe Instant.parse("2023-04-01T17:30:00.00Z")
        }
    }
})


