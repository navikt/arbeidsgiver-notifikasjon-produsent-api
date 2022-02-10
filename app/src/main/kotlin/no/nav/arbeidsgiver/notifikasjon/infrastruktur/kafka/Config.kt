package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.strictObjectMapper
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.lang.System.getenv
import org.apache.kafka.clients.CommonClientConfigs as CommonProp
import org.apache.kafka.clients.consumer.ConsumerConfig as ConsumerProp
import org.apache.kafka.clients.producer.ProducerConfig as ProducerProp
import org.apache.kafka.common.config.SslConfigs as SSLProp

typealias KafkaKey = String

const val TOPIC = "fager.notifikasjon"

interface JsonSerializer<T> : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        return laxObjectMapper.writeValueAsBytes(data)
    }
}

abstract class JsonDeserializer<T>(private val clazz: Class<T>) : Deserializer<T> {
    private val log = logger()

    override fun deserialize(topic: String?, data: ByteArray?): T {
        try {
            return strictObjectMapper.readValue(data, clazz)
        } catch (e: Exception) {
            log.error("strict deserialize failed with message: {}", e.message)
        }
        return laxObjectMapper.readValue(data, clazz)
    }
}

class ValueSerializer : JsonSerializer<Hendelse>

class ValueDeserializer : JsonDeserializer<Hendelse>(Hendelse::class.java)

val COMMON_PROPERTIES = mapOf(
    CommonProp.BOOTSTRAP_SERVERS_CONFIG to (getenv("KAFKA_BROKERS") ?: "localhost:9092"),
    CommonProp.RETRY_BACKOFF_MS_CONFIG to "500",
    CommonProp.RECONNECT_BACKOFF_MS_CONFIG to "500",
    CommonProp.RECONNECT_BACKOFF_MAX_MS_CONFIG to "5000",
)

val SSL_PROPERTIES = if (getenv("KAFKA_KEYSTORE_PATH").isNullOrBlank())
    emptyMap()
else
    mapOf(
        CommonProp.SECURITY_PROTOCOL_CONFIG to "SSL",
        SSLProp.SSL_KEYSTORE_LOCATION_CONFIG to getenv("KAFKA_KEYSTORE_PATH"),
        SSLProp.SSL_KEYSTORE_PASSWORD_CONFIG to getenv("KAFKA_CREDSTORE_PASSWORD"),
        SSLProp.SSL_TRUSTSTORE_LOCATION_CONFIG to getenv("KAFKA_TRUSTSTORE_PATH"),
        SSLProp.SSL_TRUSTSTORE_PASSWORD_CONFIG to getenv("KAFKA_CREDSTORE_PASSWORD"),
    )

val PRODUCER_PROPERTIES = COMMON_PROPERTIES + SSL_PROPERTIES + mapOf(
    ProducerProp.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.canonicalName,
    ProducerProp.VALUE_SERIALIZER_CLASS_CONFIG to ValueSerializer::class.java.canonicalName,
    ProducerProp.PARTITIONER_CLASS_CONFIG to OrgnrPartitioner::class.java.canonicalName,
    ProducerProp.MAX_BLOCK_MS_CONFIG to 5_000,
    ProducerProp.ACKS_CONFIG to "all",
)

val CONSUMER_PROPERTIES = COMMON_PROPERTIES + SSL_PROPERTIES + mapOf(
    ConsumerProp.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.canonicalName,
    ConsumerProp.VALUE_DESERIALIZER_CLASS_CONFIG to ValueDeserializer::class.java.canonicalName,

    ConsumerProp.AUTO_OFFSET_RESET_CONFIG to "earliest",
    ConsumerProp.MAX_POLL_RECORDS_CONFIG to 50,
    ConsumerProp.MAX_POLL_INTERVAL_MS_CONFIG to Int.MAX_VALUE,
    ConsumerProp.ENABLE_AUTO_COMMIT_CONFIG to "false"
)
