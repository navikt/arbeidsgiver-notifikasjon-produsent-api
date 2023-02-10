package no.nav.arbeidsgiver.notifikasjon.bruker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClient
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClientImpl
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleService
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NærmesteLederKafkaListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.launchProcessingLoop
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Duration

object BrukerWriter {
    private val log = logger()
    val databaseConfig = Database.config(
        "bruker_model",
        envPrefix = "DB_BRUKER_API_KAFKA_USER"
    )
//
//    private val hendelsesstrøm by lazy {
//        HendelsesstrømKafkaImpl(
//            topic = NOTIFIKASJON_TOPIC,
//            groupId = "bruker-model-builder-2",
//            replayPeriodically = true,
//        )
//    }

    fun main(
//        altinnRolleClient: AltinnRolleClient = AltinnRolleClientImpl(),
        httpPort: Int = 8080
    ) {
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            launch {
                brukerRepositoryAsync.await()
                log.info("successfully connected to database, it seems")
            }

//            launch {
//                val brukerRepository = brukerRepositoryAsync.await()
//                hendelsesstrøm.forEach { event ->
//                    brukerRepository.oppdaterModellEtterHendelse(event)
//                }
//            }
//
//            launch {
//                val brukerRepository = brukerRepositoryAsync.await()
//                NærmesteLederKafkaListener().forEach { event ->
//                    brukerRepository.oppdaterModellEtterNærmesteLederLeesah(event)
//                }
//            }
//
//            val altinnRolleService = async<AltinnRolleService> {
//                AltinnRolleServiceImpl(altinnRolleClient, brukerRepositoryAsync.await().altinnRolle)
//            }
//
//
//            launchProcessingLoop(
//                "last Altinnroller",
//                pauseAfterEach = Duration.ofDays(1),
//            ) {
//                altinnRolleService.await().lastRollerFraAltinn()
//            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
