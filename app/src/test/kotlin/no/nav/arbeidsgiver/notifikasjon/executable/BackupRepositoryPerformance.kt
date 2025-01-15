package no.nav.arbeidsgiver.notifikasjon.executable

import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.BackupRepository
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.KafkaBackup
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.random.Random

fun main() = runBlocking {
    Database.openDatabase(
        KafkaBackup.databaseConfig.copy(
            // https://github.com/flyway/flyway/issues/2323#issuecomment-804495818
            jdbcOpts = mapOf("preparedStatementCacheQueries" to "0")

        )
    ).use { database ->
        val backupRepository = BackupRepository(database)
        val eventIds = mutableSetOf<UUID>()

        reportTime("writing", batches = 100, batchSize = 1000) {
            val record = if (eventIds.isNotEmpty() && Random.nextDouble() < 0.1) {
                val id = eventIds.random()

                ConsumerRecord<ByteArray, ByteArray>(
                    "topic",
                    1,
                    1L,
                    id.toString().toByteArray(),
                    null,
                )
            } else {
                val id = UUID.randomUUID()
                eventIds.add(id)
                val event = EksempelHendelse.BeskjedOpprettet.copy(
                    hendelseId = id,
                    notifikasjonId = id,
                    eksternId = id.toString(),
                )

                ConsumerRecord(
                    "topic",
                    1,
                    1L,
                    id.toString().toByteArray(),
                    laxObjectMapper.writeValueAsBytes(event)
                )
            }

            backupRepository.process(record)
        }
    }
}


suspend fun reportTime(name: String, batches: Int, batchSize: Int, action: suspend () -> Unit) {
    val start = Instant.now()
    for (batch in 1 .. batches) {
        val batchStart = Instant.now()
        for (i in 0 .. batchSize) {
            action()
        }
        val batchTime = Duration.between(batchStart, Instant.now())
        println("[$name] batch $batch time: $batchTime")
        println("${" ".repeat(2 + name.length)} ${batchTime.toMillis() / batchSize.toLong()} ms/op")
    }
    val time = Duration.between(start, Instant.now())
    println("[$name] total time: $time")
}


