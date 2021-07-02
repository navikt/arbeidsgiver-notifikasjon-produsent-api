package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.*
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.toThePowerOf
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule

interface CoroutineKafkaConsumer<K, V> {
    suspend fun forEachEvent(body: suspend (V) -> Unit)
    suspend fun poll(timeout: Duration): ConsumerRecords<K, V>
}

private fun <T> ConcurrentLinkedQueue<T>.pollAll(): List<T> =
    generateSequence {
        this.poll()
    }.toList()

fun createKafkaConsumer(configure: Properties.() -> Unit = {}): CoroutineKafkaConsumer<KafkaKey, Hendelse> {
    val properties = Properties().apply {
        putAll(CONSUMER_PROPERTIES)
        configure()
    }
    val kafkaConsumer = KafkaConsumer<KafkaKey, Hendelse>(properties)
    KafkaClientMetrics(kafkaConsumer).bindTo(Health.meterRegistry)
    kafkaConsumer.subscribe(listOf(TOPIC))
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

    override suspend fun forEachEvent(body: suspend (V) -> Unit) {
        while (true) {
            consumer.resume(resumeQueue.pollAll())
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
                    body(recordValue)
                    consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
                    log.info("successfully processed {}", record.loggableToString())
                    retries.set(0)
                    return@currentRecord
                } catch (e: RuntimeException) {
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
                Health.meterRegistry.gauge(
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
