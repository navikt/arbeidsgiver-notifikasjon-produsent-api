package no.nav.arbeidsgiver.notifikasjon.executable.kafka_reaper

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaper

/* Bruker API */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    KafkaReaper.main(
        httpPort = 8083
    )
}

