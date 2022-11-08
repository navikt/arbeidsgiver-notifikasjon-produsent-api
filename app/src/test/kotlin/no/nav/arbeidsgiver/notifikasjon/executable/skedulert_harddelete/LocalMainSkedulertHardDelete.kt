package no.nav.arbeidsgiver.notifikasjon.executable.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.executable.Port
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDelete


fun main() {
    SkedulertHardDelete.main(
        httpPort = Port.SKEDULERT_HARDDELETE.port,
    )
}