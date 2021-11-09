package no.nav.arbeidsgiver.notifikasjon.util

import java.util.*


private const val UUID_BASE: String = "da89eafe-b31b-11eb-8529-000000000000"

fun String.replaceSuffix(suffix: String): String =
    removeRange(length - suffix.length, length) + suffix

fun uuid(suffix: String): UUID =
    UUID.fromString(UUID_BASE.replaceSuffix(suffix))

fun main() {
    println("hey".replaceSuffix("")) // => hey
    println("hey".replaceSuffix("j")) // => hej
    println("hey".replaceSuffix("ii")) // => hii
}



