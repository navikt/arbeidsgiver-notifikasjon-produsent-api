package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.localKafka
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupHendelserTest {
    companion object {
        @JvmField
        @RegisterExtension
        val kafka = localKafka()
    }

    @Test
    fun `write to and read from database`() = withTestDatabase(KafkaBackup.databaseConfig) { database ->
        val backupRepository = BackupRepository(database)
        val producer = kafka.newProducer()
        var eventsSent = 0
        var eventsRead = 0

        EksempelHendelse.Alle.forEach {
            producer.send(it)
            eventsSent += 1
        }

        // kan lese alle med raw consumer
        val consumer = kafka.newRawConsumer()
        val stop = AtomicBoolean(false)
        consumer.forEach(stop) {
            eventsRead += 1
            backupRepository.process(it)
            stop.set(eventsRead >= eventsSent)
        }
        assertEquals(eventsSent, eventsRead)
    }
}