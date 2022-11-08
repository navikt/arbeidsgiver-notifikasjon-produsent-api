package no.nav.arbeidsgiver.notifikasjon.executable.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.executable.Port
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaper

/* Bruker API */
fun main() {
    KafkaReaper.main(
        httpPort = Port.KAFKA_REAPER.port,
    )
}

