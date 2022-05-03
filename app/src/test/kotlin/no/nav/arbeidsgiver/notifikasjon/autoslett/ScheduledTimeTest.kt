package no.nav.arbeidsgiver.notifikasjon.autoslett

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import java.time.Instant

class ScheduledTimeTest : DescribeSpec({
    describe("Spec er en konkret dato") {
        val schedTime = ScheduledTime(
            HendelseModel.LocalDateTimeOrDuration.LocalDateTime(
                java.time.LocalDateTime.parse("2022-04-01T12:30")
            ),
            Instant.MAX
        )
        it("HappensAt regner ut riktig UTC-tidspunkt") {
            schedTime.happensAt() shouldBe Instant.parse("2022-04-01T10:30:00.00Z")
        }
    }

    describe("Spec er en duration") {
        val schedTime = ScheduledTime(
            HendelseModel.LocalDateTimeOrDuration.Duration(
                ISO8601Period.parse("PT5H")
            ),
            Instant.parse("2022-04-01T12:30:00.00Z")
        )
        it("HappensAt regner ut riktig UTC-tidspunkt") {
            schedTime.happensAt() shouldBe Instant.parse("2022-04-01T17:30:00.00Z")
        }
    }
})
