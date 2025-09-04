package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC

object Dataprodukt {
    val databaseConfig = Database.config("dataprodukt_model")

    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "dataprodukt-model-builder-3",
            replayPeriodically = true,
        )
    }

    private val saltVerdi = System.getenv("SALT_VERDI")
        ?: error("Missing required environment variable: SALT_VERDI")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig) {
                placeholders(
                    mapOf("SALT_VERDI" to saltVerdi),
                )
            }

            launch {
                val dataproduktModel = DataproduktModel(databaseDeferred.await())
                hendelsesstrøm.forEach { hendelse, metadata ->
                    dataproduktModel.oppdaterModellEtterHendelse(hendelse, metadata)
                }
            }

            configureRouting { }
        }.start(wait = true)
    }
}
