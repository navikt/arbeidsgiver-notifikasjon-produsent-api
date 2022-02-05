package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.LokalOsloTidImpl.nesteDagtidIkkeSøndag
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.LokalOsloTidImpl.nesteNksÅpningstid
import java.time.DayOfWeek
import java.time.DayOfWeek.*
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

class LokalOsloTidTests: DescribeSpec({
    val dateTime = LokalOsloTidImpl.nå()

    val kl0829 = dateTime.withHour(8).withMinute(29)
    val kl0830 = dateTime.withHour(8).withMinute(30)

    val kl0859 = dateTime.withHour(8).withMinute(59)
    val kl0900 = dateTime.withHour(9).withMinute(0)
    val kl1430 = dateTime.withHour(14).withMinute(30)
    val kl1431 = dateTime.withHour(14).withMinute(31)

    val kl1600 = dateTime.withHour(16).withMinute(0)
    val kl1601 = dateTime.withHour(16).withMinute(1)

    describe("LocalDateTime.erNksÅpningstid") {
        context("når tidspunkt er innenfor NKS åningstid") {
            forAll(
                kl0830.next(MONDAY),
                kl0830.next(TUESDAY),
                kl0830.next(WEDNESDAY),
                kl0830.next(THURSDAY),
                kl0830.next(FRIDAY),

                kl1430.next(MONDAY),
                kl1430.next(TUESDAY),
                kl1430.next(WEDNESDAY),
                kl1430.next(THURSDAY),
                kl1430.next(FRIDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erNksÅpningstid() shouldBe true
                }
            }
        }

        context("når tidspunkt ikke er innenfor NKS åningstid") {
            forAll(
                kl0829.next(MONDAY),
                kl0829.next(TUESDAY),
                kl0829.next(WEDNESDAY),
                kl0829.next(THURSDAY),
                kl0829.next(FRIDAY),

                kl1431.next(MONDAY),
                kl1431.next(TUESDAY),
                kl1431.next(WEDNESDAY),
                kl1431.next(THURSDAY),
                kl1431.next(FRIDAY),

                kl0830.next(SATURDAY),
                kl0830.next(SUNDAY),

                kl1430.next(SATURDAY),
                kl1430.next(SUNDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erNksÅpningstid() shouldBe false
                }
            }
        }
    }

    describe("LocalDateTime.erDagtidIkkeSøndag") {
        context("når tidspunkt er dagtid ikke søndag") {
            forAll(
                kl0900.next(MONDAY),
                kl0900.next(TUESDAY),
                kl0900.next(WEDNESDAY),
                kl0900.next(THURSDAY),
                kl0900.next(FRIDAY),
                kl0900.next(SATURDAY),

                kl1600.next(MONDAY),
                kl1600.next(TUESDAY),
                kl1600.next(WEDNESDAY),
                kl1600.next(THURSDAY),
                kl1600.next(FRIDAY),
                kl1600.next(SATURDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erDagtidIkkeSøndag() shouldBe true
                }
            }
        }

        context("når tidspunkt er noe annet enn dagtid ikke søndag") {
            forAll(
                kl0859.next(MONDAY),
                kl0859.next(TUESDAY),
                kl0859.next(WEDNESDAY),
                kl0859.next(THURSDAY),
                kl0859.next(FRIDAY),
                kl0859.next(SATURDAY),

                kl1601.next(MONDAY),
                kl1601.next(TUESDAY),
                kl1601.next(WEDNESDAY),
                kl1601.next(THURSDAY),
                kl1601.next(FRIDAY),
                kl1601.next(SATURDAY),

                kl0900.next(SUNDAY),

                kl1600.next(SUNDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erDagtidIkkeSøndag() shouldBe false
                }
            }
        }
    }

    describe("LocalDateTime.nesteNksÅpningstid") {
        context("finner neste nks åpningstid") {
            forAll(
                kl0829.next(SATURDAY) to kl0829.withHour(9).next(MONDAY),
                kl0829.next(SUNDAY) to kl0829.withHour(9).next(MONDAY),
                kl0829.next(MONDAY) to kl0829.withHour(9).next(MONDAY),

                kl0900.next(SATURDAY) to kl0900.next(MONDAY),
                kl0900.next(SUNDAY) to kl0900.next(MONDAY),

                kl1430.next(SATURDAY) to kl0830.next(MONDAY),
                kl1430.next(SUNDAY) to kl0830.next(MONDAY),
                kl1430.next(MONDAY) to kl1430.next(MONDAY),

                kl1431.next(SATURDAY) to kl1431.withHour(8).next(MONDAY),
                kl1431.next(SUNDAY) to kl1431.withHour(8).next(MONDAY),
            ) { p ->
                it("${p.first.dayOfWeek} ${p.first.month} ${p.first.dayOfMonth}  ${p.first.toLocalTime()} -> ${p.second.dayOfWeek} ${p.second.month} ${p.second.dayOfMonth} ${p.second.toLocalTime()}") {
                    nesteNksÅpningstid(p.first) shouldBe p.second
                }
            }
        }
    }

    describe("LocalDateTime.nesteDagtidIkkeSøndag") {
        context("finner neste dagtid ikke søndag") {
            forAll(
                kl0859.next(SATURDAY) to kl0859.withHour(9).next(SATURDAY),
                kl0859.next(SUNDAY) to kl0859.withHour(9).next(MONDAY),
                kl0859.next(MONDAY) to kl0859.withHour(9).next(MONDAY),

                kl0900.next(SATURDAY) to kl0900.next(SATURDAY),
                kl0900.next(SUNDAY) to kl0900.next(MONDAY),

                kl1600.next(SATURDAY) to kl1600.next(SATURDAY),
                kl1600.next(SUNDAY) to kl0900.next(MONDAY),
                kl1600.next(MONDAY) to kl1600.next(MONDAY),

                kl1601.next(SATURDAY) to kl1601.withHour(9).next(MONDAY),
                kl1600.next(SUNDAY) to kl0900.next(MONDAY),
            ) { p ->
                it("${p.first.dayOfWeek} ${p.first.month} ${p.first.dayOfMonth} ${p.first.toLocalTime()} -> ${p.second.dayOfWeek} ${p.second.month} ${p.second.dayOfMonth} ${p.second.toLocalTime()}") {
                    nesteDagtidIkkeSøndag(p.first) shouldBe p.second
                }
            }
        }
    }
})

fun LocalDateTime.next(dayOfWeek: DayOfWeek) : LocalDateTime =
    if (this.dayOfWeek == dayOfWeek) this else with(TemporalAdjusters.next(dayOfWeek))
