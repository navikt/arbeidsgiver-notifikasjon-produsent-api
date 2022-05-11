package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import java.util.concurrent.atomic.AtomicBoolean

interface RawKafkaReader {
    suspend fun forEach(stop: AtomicBoolean = AtomicBoolean(false), action: suspend (ConsumerRecord<ByteArray, ByteArray>) -> Unit)
}

class RawKafkaReaderImpl(topic: String, groupId: String) : RawKafkaReader {
    private val consumer: CoroutineKafkaConsumer<ByteArray, ByteArray> = CoroutineKafkaConsumer.new(
        topic = topic,
        groupId = groupId,
        keyDeserializer = ByteArrayDeserializer::class.java,
        valueDeserializer = ByteArrayDeserializer::class.java,
    )

    override suspend fun forEach(
        stop: AtomicBoolean,
        action: suspend (ConsumerRecord<ByteArray, ByteArray>) -> Unit
    ) {
        consumer.forEach(stop) {
            action(it)
        }
    }
}