package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC

object Dataprodukt {
    val databaseConfig = Database.config("dataprodukt_model")
    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "dataprodukt-model-builder-2",
            replayPeriodically = true,
        )
    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)

            launch {
                val dataproduktModel = DataproduktModel(database.await())
                hendelsesstrøm.forEach { hendelse, metadata ->
                    dataproduktModel.oppdaterModellEtterHendelse(hendelse, metadata)
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
