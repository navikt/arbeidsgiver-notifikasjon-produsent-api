package no.nav.arbeidsgiver.notifikasjon.executable.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarsling
import no.nav.arbeidsgiver.notifikasjon.executable.Port

fun main() {
    EksternVarsling.main(
        httpPort = Port.EKSTERN_VARSKING.port,
    )
}

