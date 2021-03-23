package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerConfig.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs.*
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory
import java.lang.System.getenv
import java.time.Duration
import java.util.*

data class KafkaKey(
    val mottaker: Mottaker
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Event {
    abstract val merkelapp: String
    abstract val eksternId: String
    abstract val mottaker: Mottaker
}

data class BeskjedOpprettet(
    override val merkelapp: String,
    override val eksternId: String,
    override val mottaker: Mottaker,

    /* nb. id-en er kun ment for å identifisere eventet */
    val guid: UUID,
    val tekst: String,
    val grupperingsid: String? = null,
    val lenke: String,
    val opprettetTidspunkt: String
): Event()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Mottaker

@JsonTypeName("fodselsnummer")
data class FodselsnummerMottaker (
    val fodselsnummer: String,
    val virksomhetsnummer: String
) : Mottaker()

@JsonTypeName("altinn")
data class AltinnMottaker(
    val altinntjenesteKode: String,
    val altinntjenesteVersjon: String,
    val virksomhetsnummer: String,
): Mottaker()

interface JsonSerializer<T> : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray {
        return objectMapper.writeValueAsBytes(data)
    }
}

abstract class JsonDeserializer<T>(private val clazz: Class<T>): Deserializer<T> {
    override fun deserialize(topic: String?, data: ByteArray?): T {
        return objectMapper.readValue(data, clazz)
    }
}

class KeySerializer : JsonSerializer<KafkaKey>
class ValueSerializer : JsonSerializer<Event>
class ValueDeserializer : JsonDeserializer<Event>(Event::class.java)
class KeyDeserializer : JsonDeserializer<KafkaKey>(KafkaKey::class.java)


const val DEFAULT_BROKER = "localhost:9092"

fun createProducer(): Producer<KafkaKey, Event> {
    val props = Properties()
    props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = getenv("KAFKA_BROKERS") ?: DEFAULT_BROKER
    props[KEY_SERIALIZER_CLASS_CONFIG] = KeySerializer::class.java.canonicalName
    props[VALUE_SERIALIZER_CLASS_CONFIG] = ValueSerializer::class.java.canonicalName
    props[MAX_BLOCK_MS_CONFIG] = 5_000

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
    ).get()
}

fun createConsumer(): Consumer<KafkaKey, Event> {
    val props = Properties()
    props["bootstrap.servers"] = getenv("KAFKA_BROKERS") ?: DEFAULT_BROKER
    props[KEY_DESERIALIZER_CLASS_CONFIG] = KeyDeserializer::class.java.canonicalName
    props["value.deserializer"] = ValueDeserializer::class.java.canonicalName
    props["ssl.keystore.location"] = getenv("KAFKA_KEYSTORE_PATH") ?: ""
    props["ssl.keystore.password"] = getenv("KAFKA_CREDSTORE_PASSWORD") ?: ""
    props["ssl.truststore.location"] = getenv("KAFKA_TRUSTSTORE_PATH") ?: ""
    props["ssl.truststore.password"] = getenv("KAFKA_CREDSTORE_PASSWORD") ?: ""
    props["security.protocol"] = "SSL"

    props["group.id"] = "query-model-builder"
    props[MAX_POLL_RECORDS_CONFIG] = "1"
    props[ENABLE_AUTO_COMMIT_CONFIG] = "false"

    return KafkaConsumer<KafkaKey, Event>(props).also {
        it.subscribe(listOf("arbeidsgiver.notifikasjon"))
    }
}

private val log = LoggerFactory.getLogger("Consumer.proces")!!

fun <K, V>Consumer<K, V>.processSingle(processor: (K, V) -> Unit) {
    /* TODO: lag guage for failed. remember parition & offset as tags */
    var failed = 0
    while (true) {
        val records = this.poll(Duration.ofMillis(1000))
        if (records.isEmpty) {
            continue
        } else if (records.count() > 1) {
            log.error("Misconfiguration, polling returned more than 1 record")
            /* TODO: this is a serious error, and should stop consuming completely
             * requires manual intervention.
             */
            throw IllegalStateException("Misconfiguration, polling returned more than 1 record")
        }


        val record = records.first()
        try {
            log.info("processing {}", record.loggableToString())
            processor(record.key(), record.value())
            this.commitSync()
            failed = 0
            log.info("successfully processed {}", record.loggableToString())
        } catch (e: Exception) {
            failed += 1
            log.error("exception while processing {}", record.loggableToString(), e)
        }
    }
}

fun <K, V> ConsumerRecord<K, V>.loggableToString() =
    """ | ConsumerRecord(
        | topic = ${topic()},
        | partition = ${partition()}, 
        | offset = ${offset()},
        | timestamp = ${timestamp()}
        | )
    """.trimMargin()
