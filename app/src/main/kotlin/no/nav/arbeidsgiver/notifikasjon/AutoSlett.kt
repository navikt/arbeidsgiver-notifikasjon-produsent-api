package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.autoslett.AutoSlettRepository
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.forEachHendelse

object AutoSlett {
    val log = logger()
    val databaseConfig = Database.config("autoslett_model")

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)

            launch {
                val model = AutoSlettRepository(database.await())
                forEachHendelse("autoslett-model-builder") { hendelse, metadata ->
                    model.oppdaterModellEtterHendelse(hendelse)
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
