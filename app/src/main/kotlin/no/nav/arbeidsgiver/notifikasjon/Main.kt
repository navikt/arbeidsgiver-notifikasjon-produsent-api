package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*

@Suppress("unused") /* Entry point for jar. */
object Main {
    val log = logger()
}

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    when (val navn = System.getenv("NAIS_APP_NAME")) {
        "notifikasjon-produsent-api" -> Produsent.main()
        "notifikasjon-bruker-api" -> Bruker.main()
        "notifikasjon-kafka-reaper" -> KafkaReaper.main()
        else -> Main.log.error("ukjent \$NAIS_APP_NAME '$navn'")
    }
}

