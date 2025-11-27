package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.RawKafkaReaderImpl
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun localKafka() = LocalKafka()
private val topic = AtomicReference(NOTIFIKASJON_TOPIC)
private val groupIdCounter = AtomicInteger(0)

class LocalKafka {
    private fun newId() = "test-" + groupIdCounter.incrementAndGet()

    init {
        topic.set("${NOTIFIKASJON_TOPIC}-${UUID.randomUUID()}")
    }

    fun newConsumer(): HendelsesstrømKafkaImpl {
        val id = newId()
        return HendelsesstrømKafkaImpl(
            topic = topic.get(),
            groupId = id,
            configOverrides = mapOf(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1000,
                CommonClientConfigs.GROUP_INSTANCE_ID_CONFIG to id,
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            )
        )
    }

    fun newRawConsumer(): RawKafkaReaderImpl {
        val id = newId()
        return RawKafkaReaderImpl(
            topic = topic.get(),
            groupId = id,
            configOverrides = mapOf(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1000,
                CommonClientConfigs.GROUP_INSTANCE_ID_CONFIG to id,
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            )
        )
    }

    fun newProducer() =
        lagKafkaHendelseProdusent(
            topic = topic.get(),
            configOverrides = mapOf(
             CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG to 15000,
             CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            )
        )
}