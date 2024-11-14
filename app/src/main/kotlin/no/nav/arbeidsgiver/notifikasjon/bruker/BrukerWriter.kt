package no.nav.arbeidsgiver.notifikasjon.bruker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NærmesteLederKafkaListener

object BrukerWriter {
    val databaseConfig = Database.config(
        "bruker_model",
        envPrefix = "DB_BRUKER_API_KAFKA_USER",
        jdbcOpts = mapOf(
            "socketFactory" to "com.google.cloud.sql.postgres.SocketFactory",
            "cloudSqlInstance" to System.getenv("CLOUD_SQL_INSTANCE")!!
        )
    )

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
        runBlocking(Dispatchers.Default) {
            val database = openDatabaseAsync(databaseConfig)
            val brukerRepositoryAsync = async {
                BrukerRepositoryImpl(database.await())
            }

            launch {
                val brukerRepository = brukerRepositoryAsync.await()
                hendelsesstrøm.forEach { event, metadata ->
                    brukerRepository.oppdaterModellEtterHendelse(event, metadata)
                }
            }

            launch {
                val brukerRepository = brukerRepositoryAsync.await()
                NærmesteLederKafkaListener().forEach { event ->
                    brukerRepository.oppdaterModellEtterNærmesteLederLeesah(event)
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}
