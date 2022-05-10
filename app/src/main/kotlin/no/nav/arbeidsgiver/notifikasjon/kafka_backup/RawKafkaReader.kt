package no.nav.arbeidsgiver.notifikasjon.kafka_backup

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord

interface RawKafkaReader {
    suspend fun forEach(action: suspend (ConsumerRecord<ByteArray, ByteArray?>) -> Unit)
}

class RawKafkaReaderImpl(topic: String, groupId: String) : RawKafkaReader {
    private val consumer = CoroutineKafkaConsumer<ByteArray, ByteArray>(
        topic = topic,
        groupId = groupId
    ) {
        // byte serde
    }

    override suspend fun forEach(action: suspend (ConsumerRecord<ByteArray, ByteArray?>) -> Unit) {
    }
}