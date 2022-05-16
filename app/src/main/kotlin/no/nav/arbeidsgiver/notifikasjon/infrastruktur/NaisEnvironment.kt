package no.nav.arbeidsgiver.notifikasjon.infrastruktur

object NaisEnvironment {
    val clientId = System.getenv("NAIS_CLIENT_ID") ?: "local:fager:notifikasjon-bruker-api"
    val clusterName: String = System.getenv("NAIS_CLUSTER_NAME") ?: ""
}