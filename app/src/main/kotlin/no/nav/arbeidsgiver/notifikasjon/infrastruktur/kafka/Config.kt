package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import java.lang.System.getenv
import org.apache.kafka.clients.CommonClientConfigs as CommonProp
import org.apache.kafka.clients.consumer.ConsumerConfig as ConsumerProp
import org.apache.kafka.clients.producer.ProducerConfig as ProducerProp
import org.apache.kafka.common.config.SslConfigs as SSLProp

typealias KafkaKey = String

const val NOTIFIKASJON_TOPIC = "fager.notifikasjon"

val kafkaObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
}

interface JsonSerializer<T> : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        return kafkaObjectMapper.writeValueAsBytes(data)
    }
}

abstract class JsonDeserializer<T>(private val clazz: Class<T>) : Deserializer<T> {
    private val log = logger()

    override fun deserialize(topic: String?, data: ByteArray?): T {
        return kafkaObjectMapper.readValue(data, clazz)
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
    ConsumerProp.AUTO_OFFSET_RESET_CONFIG to "earliest",
    ConsumerProp.MAX_POLL_RECORDS_CONFIG to 50,
    ConsumerProp.ENABLE_AUTO_COMMIT_CONFIG to "false"
)
