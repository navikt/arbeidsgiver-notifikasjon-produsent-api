package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.toThePowerOf
import no.nav.arbeidsgiver.notifikasjon.Produsent
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule

interface CoroutineKafkaConsumer<K, V> {
    suspend fun forEachEvent(body: suspend (V, HendelseMetadata) -> Unit)

    suspend fun forEachEvent(body: suspend (V) -> Unit) {
        forEachEvent { v: V, _: HendelseMetadata ->
            body(v)
        }
    }

    suspend fun poll(timeout: Duration): ConsumerRecords<K, V>

    suspend fun seekToBeginningOnAssignment()
}

private fun <T> ConcurrentLinkedQueue<T>.pollAll(): List<T> =
    generateSequence {
        this.poll()
    }.toList()

suspend inline fun forEachHendelse(groupId: String, crossinline body: suspend (Hendelse, HendelseMetadata) -> Unit) =
    if (System.getenv("ENABLE_KAFKA_CONSUMERS") == "false") {
        Produsent.log.info("KafkaConsumer er deaktivert.")
    } else {
        createKafkaConsumer(groupId).forEachEvent { hendelse, metadata ->
            body(hendelse, metadata)
        }
    }

suspend inline fun forEachHendelse(groupId: String, crossinline body: suspend (Hendelse) -> Unit) =
    forEachHendelse(groupId) { hendelse, _ ->
        body(hendelse)
    }

fun createKafkaConsumer(groupId: String) =
    createKafkaConsumer {
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    }

fun createKafkaConsumer(configure: Properties.() -> Unit = {}) =
    createAndSubscribeKafkaConsumer<KafkaKey, Hendelse>(TOPIC, configure = configure)

fun <K, V> createAndSubscribeKafkaConsumer(
    vararg topic: String,
    configure: Properties.() -> Unit = {}
): CoroutineKafkaConsumer<K, V> {
    val properties = Properties().apply {
        putAll(CONSUMER_PROPERTIES)
        configure()
    }
    val kafkaConsumer = KafkaConsumer<K, V>(properties)
    KafkaClientMetrics(kafkaConsumer).bindTo(Metrics.meterRegistry)
    kafkaConsumer.subscribe(topic.asList())
    return CoroutineKafkaConsumerImpl(kafkaConsumer)
}

class CoroutineKafkaConsumerImpl<K, V>(
    private val consumer: Consumer<K, V>
) : CoroutineKafkaConsumer<K, V> {
    private val log = logger()

    private val retriesPerPartition = ConcurrentHashMap<Int, AtomicInteger>()

    private val resumeQueue = ConcurrentLinkedQueue<TopicPartition>()

    private val retryTimer = Timer()

    override suspend fun poll(timeout: Duration): ConsumerRecords<K, V> =
        withContext(Dispatchers.IO) {
            consumer.poll(timeout)
        }

    override suspend fun forEachEvent(body: suspend (V, HendelseMetadata) -> Unit) {
        while (!Health.terminating) {
            consumer.resume(resumeQueue.pollAll())
            val records = try {
                poll(Duration.ofMillis(1000))
            } catch (e: Exception) {
                log.error("Unrecoverable error during poll {}", consumer.assignment(), e)
                throw e
            }

            forEachEvent(records, body)
        }
        log.info("kafka consumer stopped")
    }

    override suspend fun seekToBeginningOnAssignment() {
        consumer.subscribe(
            consumer.subscription(),
            object: ConsumerRebalanceListener {
                override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) {
                    consumer.seekToBeginning(partitions.orEmpty())
                }
                override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) {
                    // noop
                }
            }
        )
    }

    private suspend fun forEachEvent(
        records: ConsumerRecords<K, V>,
        body: suspend (V, HendelseMetadata) -> Unit
    ) {
        if (records.isEmpty) {
            return
        }

        records.partitions().forEach currentPartition@{ partition ->
            val retries = retriesForPartition(partition.partition())
            records.records(partition).forEach currentRecord@{ record ->
                try {
                    val recordValue = record.value()
                    if (recordValue == null) {
                        log.info("skipping tombstoned event key=${record.loggableToString()}")
                        return@currentRecord
                    }
                    log.info("processing {}", record.loggableToString())
                    body(recordValue, HendelseMetadata(Instant.ofEpochMilli(record.timestamp())))
                    consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
                    log.info("successfully processed {}", record.loggableToString())
                    retries.set(0)
                    return@currentRecord
                } catch (e: Exception) {
                    val attempt = retries.incrementAndGet()
                    val backoffMillis = 1000L * 2.toThePowerOf(attempt)
                    log.error(
                        "exception while processing {}. attempt={}. backoff={}.",
                        record.loggableToString(),
                        attempt,
                        Duration.ofMillis(backoffMillis),
                        e
                    )
                    val currentPartition = TopicPartition(record.topic(), record.partition())
                    consumer.seek(currentPartition, record.offset())
                    consumer.pause(listOf(currentPartition))
                    retryTimer.schedule(backoffMillis) {
                        resumeQueue.offer(currentPartition)
                    }
                    return@currentPartition
                }
            }
        }
    }

    private fun retriesForPartition(partition: Int) =
        retriesPerPartition.getOrPut(partition) {
            AtomicInteger(0).also { retries ->
                Metrics.meterRegistry.gauge(
                    "kafka_consumer_retries_per_partition",
                    Tags.of(Tag.of("partition", partition.toString())),
                    retries
                )
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
