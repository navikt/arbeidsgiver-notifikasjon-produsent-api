package no.nav.arbeidsgiver.notifikasjon.executable.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.KafkaReaper

/* Bruker API */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    KafkaReaper.main(
        httpPort = 8083
    )
}

