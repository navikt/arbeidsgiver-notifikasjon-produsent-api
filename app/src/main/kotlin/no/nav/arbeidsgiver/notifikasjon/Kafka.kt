package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.hendelse.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.Event
import no.nav.arbeidsgiver.notifikasjon.hendelse.Mottaker
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
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
import org.apache.kafka.clients.consumer.OffsetAndMetadata


data class KafkaKey(
    val mottaker: Mottaker
)

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


fun <K, V> Producer<K, V>.sendSync(record: ProducerRecord<K, V>) {
    this.send(record).get()
}

fun <K, V> Producer<K, V>.sendEvent(key: K, value: V) {
    this.sendSync(
        ProducerRecord(
            "arbeidsgiver.notifikasjon",
            key,
            value
        )
    )
}

fun Producer<KafkaKey, Event>.beskjedOpprettet(beskjed: BeskjedOpprettet) {
    sendEvent(KafkaKey(beskjed.mottaker), beskjed)
}

fun createConsumer(): Consumer<KafkaKey, Event> {
    val props = Properties()
    props["bootstrap.servers"] = getenv("KAFKA_BROKERS") ?: DEFAULT_BROKER
    props[KEY_DESERIALIZER_CLASS_CONFIG] = KeyDeserializer::class.java.canonicalName
    props["value.deserializer"] = ValueDeserializer::class.java.canonicalName
    getenv("KAFKA_KEYSTORE_PATH")?.let { props[SSL_KEYSTORE_LOCATION_CONFIG] = it }
    getenv("KAFKA_CREDSTORE_PASSWORD")?.let { props[SSL_KEYSTORE_PASSWORD_CONFIG] = it }
    getenv("KAFKA_TRUSTSTORE_PATH")?.let { props[SSL_TRUSTSTORE_LOCATION_CONFIG] = it }
    getenv("KAFKA_CREDSTORE_PASSWORD")?.let { props[SSL_TRUSTSTORE_PASSWORD_CONFIG] = it }
    if (props[SSL_KEYSTORE_LOCATION_CONFIG] != null) {
        props[SECURITY_PROTOCOL_CONFIG] = "SSL"
    }

    /* TODO: dette er midlertidig. Fjernes når query-modellen er lagret og delt
     * mellom pods. */

    props[AUTO_OFFSET_RESET_CONFIG] = "earliest"
    props[GROUP_ID_CONFIG] = "query-model-builder" + UUID.randomUUID().toString()
    props[MAX_POLL_RECORDS_CONFIG] = "1"
    props[ENABLE_AUTO_COMMIT_CONFIG] = "false"

    return KafkaConsumer<KafkaKey, Event>(props).also { consumer ->
        consumer.subscribe(listOf("arbeidsgiver.notifikasjon"))
    }
}

private val log = LoggerFactory.getLogger("Consumer.processSingle")!!

suspend fun <K, V>Consumer<K, V>.processSingle(processor: (V) -> Unit) {
    /* TODO: lag guage for failed. remember parition & offset as tags */
    while (true) {
        val records = try {
            poll(Duration.ofMillis(1000))
        } catch (e: Exception) {
            log.error("Unrecoverable error during poll {}", assignment(), e)
            throw e
        }

        processWithRetry(records, processor)
    }
}

private suspend fun <K, V> Consumer<K, V>.processWithRetry(
    records: ConsumerRecords<K, V>,
    processor: (V) -> Unit
) {
    if (records.isEmpty) {
        return
    }

    records.partitions().forEach { partition ->
        records.records(partition).forEach { record ->
            var failed = 0
            var success = false
            var backoffMillis = 5000L
            val backoffMultiplier = 2
            while (!success) {
                try {
                    if (failed > 0) {
                        // TODO: metric på antall feil (gauge?)
                        backoffMillis += backoffMillis * backoffMultiplier
                        log.warn(
                            "failed record(key={},partition={},offset={},timestamp={}) {} times. retrying with backoff={}",
                            record.key(),
                            record.partition(),
                            record.offset(),
                            record.timestamp(),
                            failed,
                            Duration.ofMillis(backoffMillis)
                        )
                        delay(backoffMillis)
                    }

                    log.info("processing {}", record.loggableToString())
                    processor(record.value())
                    commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
                    success = true
                    log.info("successfully processed {}", record.loggableToString())
                } catch (e: Exception) {
                    failed += 1
                    log.error("exception while processing {}", record.loggableToString(), e)
                }
            }
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
