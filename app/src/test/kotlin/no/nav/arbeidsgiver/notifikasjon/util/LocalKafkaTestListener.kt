package no.nav.arbeidsgiver.notifikasjon.util

import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.RawKafkaReaderImpl
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun localKafka() = LocalKafkaTestListener()
private val topic = AtomicReference(NOTIFIKASJON_TOPIC)
private val groupIdCounter = AtomicInteger(0)

class LocalKafkaTestListener: BeforeEachCallback {
    private fun newId() = "test-" + groupIdCounter.incrementAndGet()

    override fun beforeEach(context: ExtensionContext) = runBlocking {
        topic.set("${NOTIFIKASJON_TOPIC}-${UUID.randomUUID()}")
    }

    fun newConsumer(): HendelsesstrømKafkaImpl {
        val id = newId()
        return HendelsesstrømKafkaImpl(
            topic = topic.get(),
            groupId = id,
        ) {
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
            this[CommonClientConfigs.GROUP_INSTANCE_ID_CONFIG] = id
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
        }
    }

    fun newRawConsumer(): RawKafkaReaderImpl {
        val id = newId()
        return RawKafkaReaderImpl(
            topic = topic.get(),
            groupId = id,
        ) {
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
            this[CommonClientConfigs.GROUP_INSTANCE_ID_CONFIG] = id
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
        }
    }

    fun newProducer() =
        lagKafkaHendelseProdusent(topic = topic.get()) {
            this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
        }
}