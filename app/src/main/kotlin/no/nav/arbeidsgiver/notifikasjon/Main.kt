package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDelete
import no.nav.arbeidsgiver.notifikasjon.bruker.Bruker
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarsling
import no.nav.arbeidsgiver.notifikasjon.hendelse_transformer.HendelseTransformer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.KafkaBackup
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.KafkaReaper
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.SkedulertPåminnelse
import no.nav.arbeidsgiver.notifikasjon.skedulert_utgått.SkedulertUtgått
import no.nav.arbeidsgiver.notifikasjon.statistikk.Statistikk
import kotlin.system.exitProcess

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
            "notifikasjon-kafka-backup" -> KafkaBackup.main()
            "notifikasjon-skedulert-utgaatt" -> SkedulertUtgått.main()
            "notifikasjon-skedulert-paaminnelse" -> SkedulertPåminnelse.main()
            "notifikasjon-skedulert-harddelete" -> SkedulertHardDelete.main()
            "notifikasjon-hendelse-transformer" -> HendelseTransformer.main()
            else -> Main.log.error("ukjent \$NAIS_APP_NAME '$navn'")
        }
    } catch (e: Exception) {
        Main.log.error("unhandled toplevel exception {}. exiting.", e.message, e)
        exitProcess(1)
    }
}

