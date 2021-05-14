package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.pow

private val WHITESPACE = Regex("\\s+")

/** Removes all occurences of whitespace [ \t\n\x0B\f\r]. */
fun String.removeAllWhitespace() =
    this.replace(WHITESPACE, "")


/** Get logger for enclosing class. */
inline fun <reified T : Any> T.logger(): Logger =
    LoggerFactory.getLogger(this::class.java)


fun Int.toThePowerOf(exponent: Int): Long = toDouble().pow(exponent).toLong()
