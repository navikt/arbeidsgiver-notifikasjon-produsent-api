package no.nav.arbeidsgiver.notifikasjon.executable.replay_validator

import no.nav.arbeidsgiver.notifikasjon.ReplayValidator
import no.nav.arbeidsgiver.notifikasjon.executable.Port

/* Bruker API */
fun main() {
    ReplayValidator.main(
        httpPort = Port.REPLAY_VALIDATOR.port,
    )
}

