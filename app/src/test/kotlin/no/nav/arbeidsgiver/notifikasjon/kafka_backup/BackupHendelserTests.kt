package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.KafkaBackup
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.embeddedKafka
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.util.concurrent.atomic.AtomicBoolean

class BackupHendelserTests: DescribeSpec({
    val database = testDatabase(KafkaBackup.databaseConfig)
    val backupRepository = BackupRepository(database)
    val kafka = embeddedKafka()
    val producer = kafka.newProducer()
    val consumer = kafka.newRawConsumer()
    val stop = AtomicBoolean(false)
    var eventsSent = 0
    var eventsRead = 0

    describe("write to and read from database") {
        EksempelHendelse.Alle.forEach {
            producer.send(it)
            eventsSent += 1
        }

        xit("kan lese alle med raw consumer") {
            consumer.forEach(stop) {
                eventsRead += 1
                backupRepository.process(it)
                stop.set(eventsRead >= eventsSent)
            }
            eventsRead shouldBe eventsSent
        }
    }
})