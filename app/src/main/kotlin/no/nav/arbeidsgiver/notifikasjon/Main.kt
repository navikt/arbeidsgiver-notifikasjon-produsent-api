package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import kotlin.system.exitProcess

@Suppress("unused") /* Entry point for jar. */
private object Main {
    val log = logger()
}

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    try {
        when (val navn = System.getenv("NAIS_APP_NAME")) {
            "notifikasjon-produsent-api" -> Produsent.main()
            "notifikasjon-bruker-api" -> Bruker.main()
            "notifikasjon-kafka-reaper" -> KafkaReaper.main()
            "notifikasjon-statistikk" -> Statistikk.main()
            "notifikasjon-ekstern-varsling" -> EksternVarsling.main()
            "notifikasjon-replay-validator" -> ReplayValidator.main()
            "notifikasjon-autoslett" -> AutoSlett.main()
            "notifikasjon-kafka-backup" -> KafkaBackup.main()
            else -> Main.log.error("ukjent \$NAIS_APP_NAME '$navn'")
        }
    } catch (e: Exception) {
        Main.log.error("unhandled toplevel exception {}. exiting.", e.message, e)
        exitProcess(1)
    }
}

