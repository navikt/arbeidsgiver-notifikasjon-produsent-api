package no.nav.arbeidsgiver.notifikasjon.executable.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.executable.Port
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.SkedulertPåminnelse


fun main() {
    SkedulertPåminnelse.main(
        httpPort = Port.SKEDULERT_PÅMINNELSE.port,
    )
}