package no.nav.arbeidsgiver.notifikasjon.executable.dataprodukt

import no.nav.arbeidsgiver.notifikasjon.dataprodukt.Dataprodukt
import no.nav.arbeidsgiver.notifikasjon.executable.Port

/* Dataprodukt */
fun main() {
    Dataprodukt.main(
        httpPort = Port.DATAPRODUKT.port,
    )
}

