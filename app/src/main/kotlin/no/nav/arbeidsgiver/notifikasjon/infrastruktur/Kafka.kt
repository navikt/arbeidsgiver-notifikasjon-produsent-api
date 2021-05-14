package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Utils
import java.lang.System.getenv
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.apache.kafka.clients.CommonClientConfigs as CommonProp
import org.apache.kafka.clients.consumer.ConsumerConfig as ConsumerProp
import org.apache.kafka.clients.producer.ProducerConfig as ProducerProp
import org.apache.kafka.common.config.SslConfigs as SSLProp

typealias KafkaKey = String

class OrgnrPartitioner: Partitioner {
    companion object {
        fun partitionForOrgnr(orgnr: String, numPartitions: Int): Int =
            Utils.toPositive(Utils.murmur2(orgnr.toByteArray())) % numPartitions
    }

    override fun partition(
        topic: String,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster
    ): Int =
        when (value) {
            is Hendelse -> partitionForOrgnr(value.virksomhetsnummer, cluster.partitionsForTopic(topic).size)
            null -> throw IllegalArgumentException("OrgnrPartition skal ikke motta tombstone-records")
            else -> throw IllegalArgumentException("Ukjent event-type ${value::class.qualifiedName}")
        }

    override fun configure(configs: MutableMap<String, *>?) {
    }

    override fun close() {
    }
}

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

class ValueSerializer : JsonSerializer<Hendelse>
class ValueDeserializer : JsonDeserializer<Hendelse>(Hendelse::class.java)

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
    ProducerProp.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.canonicalName,
    ProducerProp.VALUE_SERIALIZER_CLASS_CONFIG to ValueSerializer::class.java.canonicalName,
    ProducerProp.PARTITIONER_CLASS_CONFIG to OrgnrPartitioner::class.java.canonicalName,
    ProducerProp.MAX_BLOCK_MS_CONFIG to 5_000,
)

private val CONSUMER_PROPERTIES = COMMON_PROPERTIES + SSL_PROPERTIES + mapOf(
    ConsumerProp.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.canonicalName,
    ConsumerProp.VALUE_DESERIALIZER_CLASS_CONFIG to ValueDeserializer::class.java.canonicalName,

    ConsumerProp.AUTO_OFFSET_RESET_CONFIG to "earliest",
    ConsumerProp.GROUP_ID_CONFIG to "query-model-builder",
    ConsumerProp.MAX_POLL_RECORDS_CONFIG to 50,
    ConsumerProp.MAX_POLL_INTERVAL_MS_CONFIG to Int.MAX_VALUE,
    ConsumerProp.ENABLE_AUTO_COMMIT_CONFIG to "false"
)

interface CoroutineProducer<K, V> {
    suspend fun send(record: ProducerRecord<K, V>): RecordMetadata
}

fun createKafkaProducer(configure: Properties.() -> Unit = {}): CoroutineProducer<KafkaKey, Hendelse> {
    val properties = Properties().apply {
        putAll(PRODUCER_PROPERTIES)
        configure()
    }
    val kafkaProducer = KafkaProducer<KafkaKey, Hendelse>(properties)
    KafkaClientMetrics(kafkaProducer).bindTo(Health.meterRegistry)
    return CoroutineProducerImpl(kafkaProducer)
}

@JvmInline
value class CoroutineProducerImpl<K, V>(
    private val producer: Producer<K, V>
) : CoroutineProducer<K, V> {


    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun send(record: ProducerRecord<K, V>): RecordMetadata {
        return suspendCancellableCoroutine { continuation ->
            val result = producer.send(record) { metadata, exception ->
                if (exception != null) {
                    continuation.cancel(exception)
                } else {
                    continuation.resume(metadata) {
                        /* nothing to close if canceled */
                    }
                }
            }

            continuation.invokeOnCancellation {
                result.cancel(true)
            }
        }
    }
}

suspend fun CoroutineProducer<KafkaKey, Hendelse>.sendHendelse(key: KafkaKey, value: Hendelse) {
    this.send(
        ProducerRecord(
            "arbeidsgiver.notifikasjon",
            key,
            value
        )
    )
}

suspend fun CoroutineProducer<KafkaKey, Hendelse>.beskjedOpprettet(beskjed: Hendelse.BeskjedOpprettet) {
    sendHendelse(beskjed.uuid.toString(), beskjed)
}

suspend fun CoroutineProducer<KafkaKey, Hendelse>.brukerKlikket(brukerKlikket: Hendelse.BrukerKlikket) {
    sendHendelse(UUID.randomUUID().toString(), brukerKlikket)
}

interface CoroutineConsumer<K, V> {
    suspend fun forEachEvent(body: suspend (V) -> Unit)
    suspend fun poll(timeout: Duration): ConsumerRecords<K, V>
}

class CoroutineConsumerImpl<K, V>(
    private val consumer: Consumer<K, V>
): CoroutineConsumer<K, V> {
    private val log = logger()

    private val retriesPerPartition = ConcurrentHashMap<Int, AtomicInteger>()

    private fun retriesForPartition(partition: Int) =
        retriesPerPartition.getOrPut(partition) {
            AtomicInteger(0).also { retries ->
                Health.meterRegistry.gauge(
                    "kafka_consumer_retries_per_partition",
                    Tags.of(Tag.of("partition", partition.toString())),
                    retries
                )
            }
        }

    override suspend fun poll(timeout: Duration): ConsumerRecords<K, V> =
        withContext(Dispatchers.IO) {
            consumer.poll(timeout)
        }

    override suspend fun forEachEvent(body: suspend (V) -> Unit) {
        while (true) {
            val records = try {
                poll(Duration.ofMillis(1000))
            } catch (e: Exception) {
                log.error("Unrecoverable error during poll {}", consumer.assignment(), e)
                throw e
            }

            forEachEvent(records, body)
        }
    }

    private suspend fun forEachEvent(
        records: ConsumerRecords<K, V>,
        body: suspend (V) -> Unit
    ) {
        if (records.isEmpty) {
            return
        }

        records.partitions().forEach { partition ->
            val retries = retriesForPartition(partition.partition())
            records.records(partition).forEach currentRecord@{ record ->
                retries.set(0)

                while (true) {
                    try {
                        log.info("processing {}", record.loggableToString())
                        body(record.value())
                        consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
                        log.info("successfully processed {}", record.loggableToString())
                        retries.set(0)
                        return@currentRecord
                    } catch (e: Exception) {
                        val attempt = retries.incrementAndGet()
                        val backoffMillis = 1000L * 2.toThePowerOf(attempt)

                        log.error("exception while processing {}. attempt={}. backoff={}.",
                            record.loggableToString(),
                            attempt,
                            Duration.ofMillis(backoffMillis),
                            e
                        )
                        delay(backoffMillis)
                    }
                }
            }
        }
    }

    private fun <K, V> ConsumerRecord<K, V>.loggableToString() = """
        ConsumerRecord(
            topic = ${topic()},
            partition = ${partition()}, 
            offset = ${offset()},
            timestamp = ${timestamp()},
            key = ${key()}
        )
    """.trimIndent()
}

fun createKafkaConsumer(configure: Properties.() -> Unit = {}): CoroutineConsumer<KafkaKey, Hendelse> {
    val properties = Properties().apply {
        putAll(CONSUMER_PROPERTIES)
        configure()
    }
    val kafkaConsumer = KafkaConsumer<KafkaKey, Hendelse>(properties)
    KafkaClientMetrics(kafkaConsumer).bindTo(Health.meterRegistry)
    kafkaConsumer.subscribe(listOf("arbeidsgiver.notifikasjon"))
    return CoroutineConsumerImpl(kafkaConsumer)
}


