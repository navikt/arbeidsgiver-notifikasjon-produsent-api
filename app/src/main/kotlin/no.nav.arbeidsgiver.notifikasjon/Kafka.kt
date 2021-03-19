package no.nav.arbeidsgiver.notifikasjon

import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs.*
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory
import java.lang.System.getenv
import java.util.*


data class Key(
    val key: String
)

data class Value(
    val value: String
)

interface JsonSerializer<T>: Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        return objectMapper.writeValueAsBytes(data)
    }
}
class KeySerializer: JsonSerializer<Key> {}
class ValueSerializer: JsonSerializer<Value> {}

private fun createProducer(): Producer<Key, Value> {
    val props = Properties()
    props[BOOTSTRAP_SERVERS_CONFIG] = getenv("KAFKA_BROKERS") ?: "localhost:9092"
    props[KEY_SERIALIZER_CLASS_CONFIG] = KeySerializer::class.java.canonicalName
    props[VALUE_SERIALIZER_CLASS_CONFIG] = ValueSerializer::class.java.canonicalName
    props[SSL_KEYSTORE_LOCATION_CONFIG] = getenv("KAFKA_KEYSTORE_PATH") ?: ""
    props[SSL_KEYSTORE_PASSWORD_CONFIG] = getenv("KAFKA_CREDSTORE_PASSWORD") ?: ""
    props[SSL_TRUSTSTORE_LOCATION_CONFIG] = getenv("KAFKA_TRUSTSTORE_PATH") ?: ""
    props[SSL_TRUSTSTORE_PASSWORD_CONFIG] = getenv("KAFKA_CREDSTORE_PASSWORD") ?: ""
    props[SECURITY_PROTOCOL_CONFIG] = "SSL"

    return KafkaProducer(props)
}

object PRODUCER {
    var logger = LoggerFactory.getLogger("PRODUCER")!!

    var producer: Producer<Key, Value> = try {
        createProducer()
    } catch (e: Exception) {
        logger.error("Konstruksjon av producer feilet", e)
        throw e
    }

    fun send(key: Key, value: Value) {
        logger.info("send({}, {})", key, value)
        producer.send(
            ProducerRecord(
                "arbeidsgiver.notifikasjoner",
                key,
                value
            )
        )
    }
}