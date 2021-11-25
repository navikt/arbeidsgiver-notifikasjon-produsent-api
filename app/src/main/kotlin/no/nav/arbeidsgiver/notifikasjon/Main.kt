package no.nav.arbeidsgiver.notifikasjon

import graphql.language.StringValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.Scalars
import java.time.OffsetDateTime

@Suppress("unused") /* Entry point for jar. */
private object Main {
    val log = logger()
}

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
//
//    val e = "2019-10-12T07:20:50.52Z"
//    println(OffsetDateTime.parse(e))
//    println(Scalars.ISO8601DateTime.coercing.parseLiteral(e))
//    println(Scalars.ISO8601DateTime.coercing.parseValue(StringValue(e)))

//    when (val navn = System.getenv("NAIS_APP_NAME")) {
//        "notifikasjon-produsent-api" -> Produsent.main()
//        "notifikasjon-bruker-api" -> Bruker.main()
//        "notifikasjon-kafka-reaper" -> KafkaReaper.main()
//        "notifikasjon-statistikk" -> Statistikk.main()
//        "notifikasjon-ekstern-varsling" -> EksternVarsling.main()
//        else -> Main.log.error("ukjent \$NAIS_APP_NAME '$navn'")
//    }
}

