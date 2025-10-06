package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabase
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC

object Dataprodukt {
    val databaseConfig = Database.config("dataprodukt_model")

    private val saltVerdi = System.getenv("SALT_VERDI")
        ?: error("Missing required environment variable: SALT_VERDI")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val database = openDatabase(databaseConfig) {
                placeholders(
                    mapOf("SALT_VERDI" to saltVerdi),
                )
            }
            val dataproduktModel = DataproduktModel(database)

            val hendelsesstrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "dataprodukt-model-builder-3",
                replayPeriodically = true,
            )

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
