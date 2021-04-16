package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.Event
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.objectMapper
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory
import java.lang.System.getenv
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import org.apache.kafka.clients.CommonClientConfigs as CommonProp
import org.apache.kafka.clients.consumer.ConsumerConfig as ConsumerProp
import org.apache.kafka.clients.producer.ProducerConfig as ProducerProp
import org.apache.kafka.common.config.SslConfigs as SSLProp

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

private val COMMON_PROPERTIES = mapOf(
    CommonProp.BOOTSTRAP_SERVERS_CONFIG to (getenv("KAFKA_BROKERS") ?: "localhost:9092"),
    CommonProp.RECONNECT_BACKOFF_MS_CONFIG to "500",
    CommonProp.RECONNECT_BACKOFF_MAX_MS_CONFIG to "5000",
)

private val SSL_PROPERTIES = if (getenv("KAFKA_KEYSTORE_PATH").isNullOrBlank())
    emptyMap()
else
    mapOf(
        CommonProp.SECURITY_PROTOCOL_CONFIG to "SSL",
        SSLProp.SSL_KEYSTORE_LOCATION_CONFIG to getenv("KAFKA_KEYSTORE_PATH"),
        SSLProp.SSL_KEYSTORE_PASSWORD_CONFIG to getenv("KAFKA_CREDSTORE_PASSWORD"),
        SSLProp.SSL_TRUSTSTORE_LOCATION_CONFIG to getenv("KAFKA_TRUSTSTORE_PATH"),
        SSLProp.SSL_TRUSTSTORE_PASSWORD_CONFIG to getenv("KAFKA_CREDSTORE_PASSWORD"),
    )

private val PRODUCER_PROPERTIES = COMMON_PROPERTIES + SSL_PROPERTIES + mapOf(
    ProducerProp.KEY_SERIALIZER_CLASS_CONFIG to KeySerializer::class.java.canonicalName,
    ProducerProp.VALUE_SERIALIZER_CLASS_CONFIG to ValueSerializer::class.java.canonicalName,
    ProducerProp.MAX_BLOCK_MS_CONFIG to 5_000,
)

private val CONSUMER_PROPERTIES = COMMON_PROPERTIES + SSL_PROPERTIES + mapOf(
    ConsumerProp.KEY_DESERIALIZER_CLASS_CONFIG to KeyDeserializer::class.java.canonicalName,
    ConsumerProp.VALUE_DESERIALIZER_CLASS_CONFIG to ValueDeserializer::class.java.canonicalName,

    ConsumerProp.AUTO_OFFSET_RESET_CONFIG to "earliest",
    ConsumerProp.GROUP_ID_CONFIG to "query-model-builder",
    ConsumerProp.MAX_POLL_RECORDS_CONFIG to 50,
    ConsumerProp.MAX_POLL_INTERVAL_MS_CONFIG to Int.MAX_VALUE,
    ConsumerProp.ENABLE_AUTO_COMMIT_CONFIG to "false"
)

fun createKafkaProducer(): Producer<KafkaKey, Event> {
    val properties = Properties().apply { putAll(PRODUCER_PROPERTIES) }
    val kafkaProducer = KafkaProducer<KafkaKey, Event>(properties)
    KafkaClientMetrics(kafkaProducer).bindTo(Health.meterRegistry)
    return kafkaProducer
}

fun createKafkaConsumer(): Consumer<KafkaKey, Event> {
    val properties = Properties().apply { putAll(CONSUMER_PROPERTIES) }
    val kafkaConsumer = KafkaConsumer<KafkaKey, Event>(properties)
    KafkaClientMetrics(kafkaConsumer).bindTo(Health.meterRegistry)
    kafkaConsumer.subscribe(listOf("arbeidsgiver.notifikasjon"))
    return kafkaConsumer
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

private val log = LoggerFactory.getLogger("Consumer.processSingle")!!

suspend fun <K, V>Consumer<K, V>.forEachEvent(processor: (V) -> Unit) {
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

val retriesPerPartition = mutableMapOf<Int, AtomicInteger>()
private suspend fun <K, V> Consumer<K, V>.processWithRetry(
    records: ConsumerRecords<K, V>,
    processor: (V) -> Unit
) {
    if (records.isEmpty) {
        return
    }

    records.partitions().forEach { partition ->
        records.records(partition).forEach { record ->
            val failed = Health.meterRegistry.gauge(
                "kafka_consumer_retries_per_partition",
                Tags.of(Tag.of("partition", "${record.partition()}")),
                retriesPerPartition.getOrPut(record.partition(), { AtomicInteger(0) })
            )
            var success = false
            var backoffMillis = 1000L
            val backoffMultiplier = 2
            while (!success) {
                try {
                    if (failed.get() > 0) {
                        backoffMillis *= backoffMultiplier
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
                    failed.set(0)
                    log.info("successfully processed {}", record.loggableToString())
                } catch (e: Exception) {
                    failed.getAndIncrement()
                    log.error("exception while processing {}", record.loggableToString(), e)
                }
            }
        }
    }
}

fun <K, V> ConsumerRecord<K, V>.loggableToString() = """
    ConsumerRecord(
        topic = ${topic()},
        partition = ${partition()}, 
        offset = ${offset()},
        timestamp = ${timestamp()}
    )
""".trimIndent()
