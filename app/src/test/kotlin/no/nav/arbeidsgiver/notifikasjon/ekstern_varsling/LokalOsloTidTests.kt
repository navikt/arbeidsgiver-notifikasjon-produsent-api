package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.LokalOsloTid.Companion.nesteDagtidIkkeSøndag
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.LokalOsloTid.Companion.nesteNksÅpningstid
import java.time.DayOfWeek
import java.time.DayOfWeek.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.MINUTES
import java.time.temporal.TemporalAdjusters

class LokalOsloTidTests: DescribeSpec({
    val dateTime = LokalOsloTid.nå().truncatedTo(MINUTES)

    describe("LocalDateTime.erNksÅpningstid") {
        context("når tidspunkt er innenfor NKS åningstid") {
            val kl0900 = dateTime.withHour(9).withMinute(0)
            val kl1459 = dateTime.withHour(14).withMinute(59)

            forAll(
                kl0900.next(MONDAY),
                kl0900.next(TUESDAY),
                kl0900.next(WEDNESDAY),
                kl0900.next(THURSDAY),
                kl0900.next(FRIDAY),

                kl1459.next(MONDAY),
                kl1459.next(TUESDAY),
                kl1459.next(WEDNESDAY),
                kl1459.next(THURSDAY),
                kl1459.next(FRIDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erNksÅpningstid() shouldBe true
                }
            }
        }

        context("når tidspunkt ikke er innenfor NKS åningstid") {
            val kl0859 = dateTime.withHour(8).withMinute(59)
            val kl0900 = dateTime.withHour(9).withMinute(0)
            val kl1459 = dateTime.withHour(14).withMinute(59)
            val kl1500 = dateTime.withHour(15).withMinute(0)

            forAll(
                kl0859.next(MONDAY),
                kl0859.next(TUESDAY),
                kl0859.next(WEDNESDAY),
                kl0859.next(THURSDAY),
                kl0859.next(FRIDAY),

                kl1500.next(MONDAY),
                kl1500.next(TUESDAY),
                kl1500.next(WEDNESDAY),
                kl1500.next(THURSDAY),
                kl1500.next(FRIDAY),

                kl0900.next(SATURDAY),
                kl0900.next(SUNDAY),

                kl1459.next(SATURDAY),
                kl1459.next(SUNDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erNksÅpningstid() shouldBe false
                }
            }
        }
    }

    describe("LocalDateTime.erDagtidIkkeSøndag") {
        context("når tidspunkt er dagtid ikke søndag") {
            val kl0900 = dateTime.withHour(9).withMinute(0)
            val kl1559 = dateTime.withHour(15).withMinute(59)

            forAll(
                kl0900.next(MONDAY),
                kl0900.next(TUESDAY),
                kl0900.next(WEDNESDAY),
                kl0900.next(THURSDAY),
                kl0900.next(FRIDAY),
                kl0900.next(SATURDAY),

                kl1559.next(MONDAY),
                kl1559.next(TUESDAY),
                kl1559.next(WEDNESDAY),
                kl1559.next(THURSDAY),
                kl1559.next(FRIDAY),
                kl1559.next(SATURDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erDagtidIkkeSøndag() shouldBe true
                }
            }
        }

        context("når tidspunkt er noe annet enn dagtid ikke søndag") {
            val kl0859 = dateTime.withHour(8).withMinute(59)
            val kl0900 = dateTime.withHour(9).withMinute(0)
            val kl1559 = dateTime.withHour(15).withMinute(59)
            val kl1600 = dateTime.withHour(16).withMinute(0)

            forAll(
                kl0859.next(MONDAY),
                kl0859.next(TUESDAY),
                kl0859.next(WEDNESDAY),
                kl0859.next(THURSDAY),
                kl0859.next(FRIDAY),
                kl0859.next(SATURDAY),

                kl1600.next(MONDAY),
                kl1600.next(TUESDAY),
                kl1600.next(WEDNESDAY),
                kl1600.next(THURSDAY),
                kl1600.next(FRIDAY),
                kl1600.next(SATURDAY),

                kl0900.next(SUNDAY),

                kl1559.next(SUNDAY),
            ) { dt ->
                it("${dt.dayOfWeek} ${dt.toLocalTime()}") {
                    dt.erDagtidIkkeSøndag() shouldBe false
                }
            }
        }
    }

    describe("LocalDateTime.nesteNksÅpningstid") {
        val kl0859 = dateTime.withHour(8).withMinute(59)
        val kl0900 = dateTime.withHour(9).withMinute(0)
        val kl1459 = dateTime.withHour(14).withMinute(59)
        val kl1500 = dateTime.withHour(15).withMinute(0)

        context("finner neste nks åpningstid") {
            forAll(
                kl0859.next(SATURDAY) to kl0859.withHour(9).next(MONDAY),
                kl0859.next(SUNDAY) to kl0859.withHour(9).next(MONDAY),
                kl0859.next(MONDAY) to kl0859.withHour(9).next(MONDAY),

                kl0900.next(SATURDAY) to kl0900.next(MONDAY),
                kl0900.next(SUNDAY) to kl0900.next(MONDAY),

                kl1459.next(SATURDAY) to kl0859.withHour(9).next(MONDAY),
                kl1459.next(SUNDAY) to kl0859.withHour(9).next(MONDAY),
                kl1459.next(MONDAY) to kl1459.next(MONDAY),

                kl1500.next(SATURDAY) to kl0900.next(MONDAY),
                kl1500.next(SUNDAY) to kl0900.next(MONDAY),
            ) { p ->
                it("${p.first.dayOfWeek} ${p.first.toLocalTime()} -> ${p.second.dayOfWeek} ${p.second.toLocalTime()}") {
                    nesteNksÅpningstid(p.first) shouldBe p.second
                }
            }
        }
    }

    describe("LocalDateTime.nesteDagtidIkkeSøndag") {
        val kl0859 = dateTime.withHour(8).withMinute(59)
        val kl0900 = dateTime.withHour(9).withMinute(0)
        val kl1559 = dateTime.withHour(15).withMinute(59)
        val kl1600 = dateTime.withHour(16).withMinute(0)

        context("finner neste dagtid ikke søndag") {
            forAll(
                kl0859.next(SATURDAY) to kl0859.withHour(9).next(SATURDAY),
                kl0859.next(SUNDAY) to kl0859.withHour(9).next(MONDAY),
                kl0859.next(MONDAY) to kl0859.withHour(9).next(MONDAY),

                kl0900.next(SATURDAY) to kl0900.next(SATURDAY),
                kl0900.next(SUNDAY) to kl0900.next(MONDAY),

                kl1559.next(SATURDAY) to kl1559.next(SATURDAY),
                kl1559.next(SUNDAY) to kl0859.withHour(9).next(MONDAY),
                kl1559.next(MONDAY) to kl1559.next(MONDAY),

                kl1600.next(SATURDAY) to kl0900.next(MONDAY),
                kl1600.next(SUNDAY) to kl0900.next(MONDAY),
            ) { p ->
                it("${p.first.dayOfWeek} ${p.first.toLocalTime()} -> ${p.second.dayOfWeek} ${p.second.toLocalTime()}") {
                    nesteDagtidIkkeSøndag(p.first) shouldBe p.second
                }
            }
        }
    }
})

fun LocalDateTime.next(dayOfWeek: DayOfWeek) : LocalDateTime = with(TemporalAdjusters.next(dayOfWeek))
