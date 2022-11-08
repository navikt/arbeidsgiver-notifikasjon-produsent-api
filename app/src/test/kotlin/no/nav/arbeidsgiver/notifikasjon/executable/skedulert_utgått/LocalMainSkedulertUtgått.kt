package no.nav.arbeidsgiver.notifikasjon.executable.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.executable.Port
import no.nav.arbeidsgiver.notifikasjon.skedulert_utgått.SkedulertUtgått


fun main() {
    SkedulertUtgått.main(
        httpPort = Port.SKEDULERT_UTGÅTT.port,
    )
}