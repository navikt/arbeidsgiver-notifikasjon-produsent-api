package no.nav.arbeidsgiver.notifikasjon.executable.replay_validator

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.ReplayValidator

/* Bruker API */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    ReplayValidator.main(
        httpPort = 8086
    )
}

