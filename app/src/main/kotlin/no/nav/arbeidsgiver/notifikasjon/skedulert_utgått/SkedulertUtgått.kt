package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer

object SkedulertUtgått {
//    val databaseConfig = Database.config("skedulert_utgatt_model")
//    private val hendelsesstrøm by lazy {
//        HendelsesstrømKafkaImpl(
//            topic = NOTIFIKASJON_TOPIC,
//            groupId = "skedulert-utgatt-model-builder",
//            replayPeriodically = true,
//        )
//    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            Health.subsystemReady[Subsystem.DATABASE] = true
//            val database = openDatabaseAsync(databaseConfig)
//            val repoAsync = async {
//                SkedulertUtgåttRepository(database.await())
//            }
//            launch {
//                val repo = repoAsync.await()
//                hendelsesstrøm.forEach { hendelse, metadata ->
//                    repo.oppdaterModellEtterHendelse(hendelse)
//                }
//            }
//
//            val service = async {
//                SkedulertUtgåttService(
//                    repository = repoAsync.await(),
//                    hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
//                )
//            }
//            launchProcessingLoop(
//                "utgaatt-oppgaver-service",
//                pauseAfterEach = Duration.ofMinutes(1)
//            ) {
//                service.await().settOppgaverUtgåttBasertPåFrist()
//            }
//            launchProcessingLoop(
//                "avholdt-kalenderavtaler-service",
//                pauseAfterEach = Duration.ofMinutes(1)
//            ) {
//                service.await().settKalenderavtalerAvholdtBasertPåTidspunkt()
//            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}