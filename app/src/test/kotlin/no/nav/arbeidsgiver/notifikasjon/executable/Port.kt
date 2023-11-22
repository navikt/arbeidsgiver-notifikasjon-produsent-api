package no.nav.arbeidsgiver.notifikasjon.executable

enum class Port(val port: Int) {
    PRODUSENT_API(8081),
    BRUKER_API(8082),
    KAFKA_REAPER(8083),
    //STATISTIKK(8084), removed
    EKSTERN_VARSKING(8085),
    REPLAY_VALIDATOR(8086),
    SKEDULERT_UTGÅTT(8087),
    SKEDULERT_HARDDELETE(8088),
    SKEDULERT_PÅMINNELSE(8089),
    DATAPRODUKT(8090),
}