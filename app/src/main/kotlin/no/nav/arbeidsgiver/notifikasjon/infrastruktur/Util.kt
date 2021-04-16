package no.nav.arbeidsgiver.notifikasjon.infrastruktur

private val WHITESPACE = Regex("\\s+")

/** Removes all occurences of whitespace [ \t\n\x0B\f\r]. */
fun String.removeAllWhitespace() =
    this.replace(WHITESPACE, "")