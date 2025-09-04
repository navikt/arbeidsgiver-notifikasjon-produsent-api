package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NærmesteLederKafkaListener

object BrukerWriter {
    val databaseConfig = Database.config("bruker_model")

    private val hendelsesstrøm by lazy {
        HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "bruker-model-builder-2",
            replayPeriodically = true,
        )
    }

    fun main(
        httpPort: Int = 8080
    ) {
        embeddedServer(CIO, port = httpPort) {
            val databaseDeferred = openDatabaseAsync(databaseConfig)
            val brukerRepositoryDeferred = async {
                BrukerRepositoryImpl(databaseDeferred.await())
            }

            launch {
                val brukerRepository = brukerRepositoryDeferred.await()
                hendelsesstrøm.forEach { event, metadata ->
                    brukerRepository.oppdaterModellEtterHendelse(event, metadata)
                }
            }

            launch {
                val brukerRepository = brukerRepositoryDeferred.await()
                NærmesteLederKafkaListener().forEach { event ->
                    brukerRepository.oppdaterModellEtterNærmesteLederLeesah(event)
                }
            }

            configureRouting { }
        }.start(wait = true)
    }
}
