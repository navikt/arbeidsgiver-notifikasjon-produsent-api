package no.nav.arbeidsgiver.notifikasjon

import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs.*
import org.apache.kafka.common.serialization.Serializer
import java.lang.System.getenv
import java.util.*


data class Key(
    val key: String
)

data class Value(
    val value: String
)

interface JsonSerializer<T> : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        return objectMapper.writeValueAsBytes(data)
    }
}

class KeySerializer : JsonSerializer<Key> {}
class ValueSerializer : JsonSerializer<Value> {}

fun createProducer(): Producer<Key, Value> {
    val props = Properties()
    props[BOOTSTRAP_SERVERS_CONFIG] = getenv("KAFKA_BROKERS") ?: "localhost:9092"
    props[KEY_SERIALIZER_CLASS_CONFIG] = KeySerializer::class.java.canonicalName
    props[VALUE_SERIALIZER_CLASS_CONFIG] = ValueSerializer::class.java.canonicalName

    getenv("KAFKA_KEYSTORE_PATH")?.let { props[SSL_KEYSTORE_LOCATION_CONFIG] = it }
    getenv("KAFKA_CREDSTORE_PASSWORD")?.let { props[SSL_KEYSTORE_PASSWORD_CONFIG] = it }
    getenv("KAFKA_TRUSTSTORE_PATH")?.let { props[SSL_TRUSTSTORE_LOCATION_CONFIG] = it }
    getenv("KAFKA_CREDSTORE_PASSWORD")?.let { props[SSL_TRUSTSTORE_PASSWORD_CONFIG] = it }
    if (props[SSL_KEYSTORE_LOCATION_CONFIG] != null) {
        props[SECURITY_PROTOCOL_CONFIG] = "SSL"
    }

    return KafkaProducer(props)
}

fun <K, V> Producer<K, V>.sendEvent(key: K, value: V) {
    this.send(
        ProducerRecord(
            "arbeidsgiver.notifikasjon",
            key,
            value
        )
    )
}