package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.hendelse.Hendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.*
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.RawKafkaReader
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.RawKafkaReaderImpl
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun TestConfiguration.localKafka(): LocalKafka = LocalKafkaTestListener().also {
    listener(it)
}

interface LocalKafka {
    fun newConsumer(): Hendelsesstrøm
    fun newRawConsumer(): RawKafkaReader
    fun newProducer(): HendelseProdusent
}

class LocalKafkaTestListener: TestListener, LocalKafka {
    private val topic = AtomicReference(NOTIFIKASJON_TOPIC)
    private val groupIdCounter = AtomicInteger(0)
    private fun newId() = "test-" + groupIdCounter.getAndIncrement()

    override suspend fun beforeSpec(spec: Spec) {
        topic.set("${NOTIFIKASJON_TOPIC}-${UUID.randomUUID()}")
    }

    override fun newConsumer(): HendelsesstrømKafkaImpl {
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

    override fun newRawConsumer(): RawKafkaReaderImpl {
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

    override fun newProducer() =
        lagKafkaHendelseProdusent(topic = topic.get()) {
            this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
        }
}