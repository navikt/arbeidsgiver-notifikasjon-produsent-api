package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC

object Dataprodukt {
    val databaseConfig = Database.config("dataprodukt_model")

    private val saltVerdi = System.getenv("SALT_VERDI")
        ?: error("Missing required environment variable: SALT_VERDI")


    fun main(httpPort: Int = 8080) = runBlocking {
        val hendelsesstrøm = HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "dataprodukt-model-builder-3",
            replayPeriodically = true,
        )
        val database = openDatabaseAsync(databaseConfig) {
            placeholders(
                mapOf("SALT_VERDI" to saltVerdi),
            )
        }.await()

        embeddedServer(CIO, port = httpPort) {
            val dataproduktModel = DataproduktModel(database)

            launch {
                hendelsesstrøm.forEach { hendelse, metadata ->
                    dataproduktModel.oppdaterModellEtterHendelse(hendelse, metadata)
                }
            }

            configureRouting { }
            registerShutdownListener()
        }.start(wait = true)
    }
}
