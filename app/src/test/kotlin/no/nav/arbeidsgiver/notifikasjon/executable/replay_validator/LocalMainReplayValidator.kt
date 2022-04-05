package no.nav.arbeidsgiver.notifikasjon.executable.replay_validator

import no.nav.arbeidsgiver.notifikasjon.ReplayValidator

/* Bruker API */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    ReplayValidator.main(
        httpPort = 8086
    )
}

