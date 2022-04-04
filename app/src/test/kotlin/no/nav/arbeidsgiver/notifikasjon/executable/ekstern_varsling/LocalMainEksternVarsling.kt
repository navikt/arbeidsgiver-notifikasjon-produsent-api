package no.nav.arbeidsgiver.notifikasjon.executable.ekstern_varsling

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarsling

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    EksternVarsling.main(
        httpPort = 8085,
    )
}

