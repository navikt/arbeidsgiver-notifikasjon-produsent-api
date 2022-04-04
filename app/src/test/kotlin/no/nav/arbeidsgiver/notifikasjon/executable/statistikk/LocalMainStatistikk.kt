package no.nav.arbeidsgiver.notifikasjon.executable.statistikk

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.statistikk.Statistikk

/* Statistikk */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    Statistikk.main(
        httpPort = 8084
    )
}

