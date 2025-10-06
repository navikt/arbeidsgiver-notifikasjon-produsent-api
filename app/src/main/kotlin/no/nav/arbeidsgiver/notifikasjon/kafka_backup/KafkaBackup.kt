package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabase
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC

object KafkaBackup {
    internal val databaseConfig = Database.config("kafka_backup_model")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, port = httpPort) {
            val hendelsestrøm: RawKafkaReader = RawKafkaReaderImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "kafka-backup-model-builder",
            )
            val database = openDatabase(databaseConfig)
            launch {
                val repository = BackupRepository(database)

                hendelsestrøm.forEach { record ->
                    repository.process(record)
                }
            }

            configureRouting { }
            registerShutdownListener()
        }.start(wait = true)
    }
}
