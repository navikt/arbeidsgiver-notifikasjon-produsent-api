package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Åpningstider.nesteDagtidIkkeSøndag
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.Åpningstider.nesteNksÅpningstid
import java.time.DayOfWeek
import java.time.DayOfWeek.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

class ÅpningstiderTests: DescribeSpec({
    fun tidspunkt(kl: String, dag: DayOfWeek) : LocalDateTime =
        LocalDateTime.of(LocalDate.parse("2022-01-01"), LocalTime.parse(kl))
            .with(TemporalAdjusters.firstInMonth(dag))

    describe("Åpningstider.erNksÅpningstid") {
        context("når tidspunkt er innenfor NKS åningstid") {
            withData(
                tidspunkt("08:30", MONDAY),
                tidspunkt("08:30", TUESDAY),
                tidspunkt("08:30", WEDNESDAY),
                tidspunkt("08:30", THURSDAY),
                tidspunkt("08:30", FRIDAY),

                tidspunkt("14:30", MONDAY),
                tidspunkt("14:30", TUESDAY),
                tidspunkt("14:30", WEDNESDAY),
                tidspunkt("14:30", THURSDAY),
                tidspunkt("14:30", FRIDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erNksÅpningstid() shouldBe true
                }
            }
        }

        context("når tidspunkt ikke er innenfor NKS åningstid") {
            withData(
                tidspunkt("08:29", MONDAY),
                tidspunkt("08:29", TUESDAY),
                tidspunkt("08:29", WEDNESDAY),
                tidspunkt("08:29", THURSDAY),
                tidspunkt("08:29", FRIDAY),

                tidspunkt("14:31", MONDAY),
                tidspunkt("14:31", TUESDAY),
                tidspunkt("14:31", WEDNESDAY),
                tidspunkt("14:31", THURSDAY),
                tidspunkt("14:31", FRIDAY),

                tidspunkt("08:30", SATURDAY),
                tidspunkt("08:30", SUNDAY),

                tidspunkt("14:30", SATURDAY),
                tidspunkt("14:30", SUNDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erNksÅpningstid() shouldBe false
                }
            }
        }
    }

    describe("LocalDateTime.erDagtidIkkeSøndag") {
        context("når tidspunkt er dagtid ikke søndag") {
            withData(
                tidspunkt("09:00", MONDAY),
                tidspunkt("09:00", TUESDAY),
                tidspunkt("09:00", WEDNESDAY),
                tidspunkt("09:00", THURSDAY),
                tidspunkt("09:00", FRIDAY),
                tidspunkt("09:00", SATURDAY),

                tidspunkt("16:00", MONDAY),
                tidspunkt("16:00", TUESDAY),
                tidspunkt("16:00", WEDNESDAY),
                tidspunkt("16:00", THURSDAY),
                tidspunkt("16:00", FRIDAY),
                tidspunkt("16:00", SATURDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erDagtidIkkeSøndag() shouldBe true
                }
            }
        }

        context("når tidspunkt er noe annet enn dagtid ikke søndag") {
            withData(
                tidspunkt("08:59", MONDAY),
                tidspunkt("08:59", TUESDAY),
                tidspunkt("08:59", WEDNESDAY),
                tidspunkt("08:59", THURSDAY),
                tidspunkt("08:59", FRIDAY),
                tidspunkt("08:59", SATURDAY),

                tidspunkt("16:01", MONDAY),
                tidspunkt("16:01", TUESDAY),
                tidspunkt("16:01", WEDNESDAY),
                tidspunkt("16:01", THURSDAY),
                tidspunkt("16:01", FRIDAY),
                tidspunkt("16:01", SATURDAY),

                tidspunkt("09:00", SUNDAY),

                tidspunkt("16:00", SUNDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erDagtidIkkeSøndag() shouldBe false
                }
            }
        }
    }

    describe("LocalDateTime.nesteNksÅpningstid") {
        context("finner neste nks åpningstid") {
            withData(
                tidspunkt("08:29",  SATURDAY) to tidspunkt("09:29", MONDAY),
                tidspunkt("08:29",  SUNDAY) to tidspunkt("09:29", MONDAY),
                tidspunkt("08:29",  MONDAY) to tidspunkt("09:29", MONDAY),

                tidspunkt("09:00", SATURDAY) to tidspunkt("09:00", MONDAY),
                tidspunkt("09:00", SUNDAY) to tidspunkt("09:00", MONDAY),

                tidspunkt("14:30", SATURDAY) to tidspunkt("08:30", MONDAY),
                tidspunkt("14:30", SUNDAY) to tidspunkt("08:30", MONDAY),
                tidspunkt("14:30", MONDAY) to tidspunkt("14:30", MONDAY),

                tidspunkt("14:31", SATURDAY) to tidspunkt("08:31", MONDAY),
                tidspunkt("14:31", SUNDAY) to tidspunkt("08:31", MONDAY),
            ) { p ->
                it("${p.first.dayOfWeek} ${p.first.toLocalTime()} -> ${p.second.dayOfWeek} ${p.second.toLocalTime()}") {
                    nesteNksÅpningstid(p.first) shouldBe p.second
                }
            }
        }
    }

    describe("LocalDateTime.nesteDagtidIkkeSøndag") {
        context("finner neste dagtid ikke søndag") {
            withData(
                tidspunkt("08:59", SATURDAY) to tidspunkt("09:59", SATURDAY),
                tidspunkt("08:59", SUNDAY) to tidspunkt("09:59", MONDAY),
                tidspunkt("08:59", MONDAY) to tidspunkt("09:59", MONDAY),

                tidspunkt("09:00", SATURDAY) to tidspunkt("09:00", SATURDAY),
                tidspunkt("09:00", SUNDAY) to tidspunkt("09:00", MONDAY),

                tidspunkt("16:00", SATURDAY) to tidspunkt("16:00", SATURDAY),
                tidspunkt("16:00", SUNDAY) to tidspunkt("09:00", MONDAY),
                tidspunkt("16:00", MONDAY) to tidspunkt("16:00", MONDAY),

                tidspunkt("16:01", SATURDAY) to tidspunkt("09:01", MONDAY),
                tidspunkt("16:00", SUNDAY) to tidspunkt("09:00", MONDAY),
            ) { p ->
                it("${p.first.dayOfWeek} ${p.first.toLocalTime()} -> ${p.second.dayOfWeek} ${p.second.toLocalTime()}") {
                    nesteDagtidIkkeSøndag(p.first) shouldBe p.second
                }
            }
        }
    }
})