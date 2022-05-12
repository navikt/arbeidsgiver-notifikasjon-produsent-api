package no.nav.arbeidsgiver.notifikasjon.executable.statistikk

import no.nav.arbeidsgiver.notifikasjon.statistikk.Statistikk

/* Statistikk */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    Statistikk.main(
        httpPort = 8084
    )
}

