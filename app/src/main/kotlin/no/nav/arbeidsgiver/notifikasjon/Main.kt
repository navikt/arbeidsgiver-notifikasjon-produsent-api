package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.bruker.Bruker
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarsling
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaper
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.statistikk.Statistikk

@Suppress("unused") /* Entry point for jar. */
private object Main {
    val log = logger()
}

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    when (val navn = System.getenv("NAIS_APP_NAME")) {
        "notifikasjon-produsent-api" -> Produsent.main()
        "notifikasjon-bruker-api" -> Bruker.main()
        "notifikasjon-kafka-reaper" -> KafkaReaper.main()
        "notifikasjon-statistikk" -> Statistikk.main()
        "notifikasjon-ekstern-varsling" -> EksternVarsling.main()
        "notifikasjon-replay-validator" -> ReplayValidator.main()
        else -> Main.log.error("ukjent \$NAIS_APP_NAME '$navn'")
    }
}
