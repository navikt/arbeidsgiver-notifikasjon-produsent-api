package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.launchHttpServer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC

object KafkaBackup {
    internal val databaseConfig = Database.config("kafka_backup_model")

    private val hendelsestrøm: RawKafkaReader by lazy {
        RawKafkaReaderImpl(
            topic = NOTIFIKASJON_TOPIC,
            groupId = "kafka-backup-model-builder",
        )
    }

    fun main(httpPort: Int = 8080) {
        runBlocking(Dispatchers.Default) {
            val databaseAsync = openDatabaseAsync(databaseConfig)

            launch {
                val repository = BackupRepository(databaseAsync.await())

                hendelsestrøm.forEach { record ->
                    repository.process(record)
                }
            }

            launchHttpServer(httpPort = httpPort)
        }
    }
}

