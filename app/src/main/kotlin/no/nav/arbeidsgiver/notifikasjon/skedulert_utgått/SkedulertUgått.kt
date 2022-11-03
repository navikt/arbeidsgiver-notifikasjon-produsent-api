package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer

object SkedulertUgått {
    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            launchHttpServer(httpPort = httpPort)
        }
    }
}