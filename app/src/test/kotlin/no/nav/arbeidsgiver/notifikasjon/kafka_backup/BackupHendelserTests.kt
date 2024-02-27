package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.localKafka
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.concurrent.atomic.AtomicBoolean

@Isolate
class BackupHendelserTests: DescribeSpec({

    describe("write to and read from database") {
        val database = testDatabase(KafkaBackup.databaseConfig)
        val backupRepository = BackupRepository(database)
        val kafka = localKafka()
        val producer = kafka.newProducer()
        var eventsSent = 0
        var eventsRead = 0

        EksempelHendelse.Alle.forEach {
            producer.send(it)
            eventsSent += 1
        }

        it("kan lese alle med raw consumer") {
            val consumer = kafka.newRawConsumer()
            val stop = AtomicBoolean(false)
            consumer.forEach(stop) {
                eventsRead += 1
                backupRepository.process(it)
                stop.set(eventsRead >= eventsSent)
            }
            eventsRead shouldBe eventsSent
        }
    }
})