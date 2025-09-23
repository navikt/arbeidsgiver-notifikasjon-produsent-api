package no.nav.arbeidsgiver.notifikasjon.bruker

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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NærmesteLederKafkaListener

object BrukerWriter {
    val databaseConfig = Database.config("bruker_model")

    fun main(
        httpPort: Int = 8080
    ) = runBlocking {
        val hendelsesstrøm = HendelsesstrømKafkaImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "bruker-model-builder-2",
            replayPeriodically = true,
        )
        val database = openDatabaseAsync(databaseConfig).await()

        embeddedServer(CIO, port = httpPort) {
            val brukerRepository = BrukerRepositoryImpl(database)

            launch {
                hendelsesstrøm.forEach { event, metadata ->
                    brukerRepository.oppdaterModellEtterHendelse(event, metadata)
                }
            }

            launch {
                NærmesteLederKafkaListener().forEach { event ->
                    brukerRepository.oppdaterModellEtterNærmesteLederLeesah(event)
                }
            }

            configureRouting { }
            registerShutdownListener()
        }.start(wait = true)
    }
}
