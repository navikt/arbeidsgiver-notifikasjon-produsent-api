package no.nav.arbeidsgiver.notifikasjon.executable.statistikk

import no.nav.arbeidsgiver.notifikasjon.executable.Port
import no.nav.arbeidsgiver.notifikasjon.statistikk.Statistikk

/* Statistikk */
fun main() {
    Statistikk.main(
        httpPort = Port.STATISTIKK.port,
    )
}

