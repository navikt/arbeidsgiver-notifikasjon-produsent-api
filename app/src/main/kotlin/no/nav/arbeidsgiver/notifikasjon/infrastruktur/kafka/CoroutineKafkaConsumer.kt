package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.consumer.ConsumerConfig.*
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.event.Level
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule
import kotlin.math.min

class CoroutineKafkaConsumer<K, V>
private constructor(
    topic: String,
    groupId: String,
    keyDeserializer: Class<*>,
    valueDeserializer: Class<*>,
    private val seekToBeginning: Boolean = false,
    replayPeriodically: Boolean = false,
    private val onPartitionAssigned: ((partition: TopicPartition, maxOffset: Long) -> Unit)?,
    private val onPartitionRevoked: ((partition: TopicPartition) -> Unit)?,
    private val configOverrides: Map<String, Any> = emptyMap(),
) {
    private val pollBodyTimer = Timer.builder("kafka.poll.body")
        .register(Metrics.meterRegistry)

    private val iterationEntryCounter = Counter.builder("kafka.poll.body.iteration")
        .tag("point", "entry")
        .register(Metrics.meterRegistry)
    private val iterationExitCounter = Counter.builder("kafka.poll.body.iteration")
        .tag("point", "exit")
        .register(Metrics.meterRegistry)

    private val kafkaContext = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    companion object {
        fun <K, V, KS : Deserializer<K>, VS: Deserializer<V>> new(
            topic: String,
            groupId: String,
            keyDeserializer: Class<KS>,
            valueDeserializer: Class<VS>,
            seekToBeginning: Boolean = false,
            replayPeriodically: Boolean = false,
            onPartitionAssigned: ((partition: TopicPartition, endOffset: Long) -> Unit)? = null,
            onPartitionRevoked: ((partition: TopicPartition) -> Unit)? = null,
            configOverrides: Map<String, Any> = emptyMap(),
        ): CoroutineKafkaConsumer<K, V> = CoroutineKafkaConsumer(
            topic,
            groupId,
            keyDeserializer,
            valueDeserializer,
            seekToBeginning,
            replayPeriodically,
            onPartitionAssigned,
            onPartitionRevoked,
            configOverrides
        )
    }

    private val consumer: Consumer<K, V> = KafkaConsumer(
        CONSUMER_PROPERTIES + mapOf<String, String>(
            KEY_DESERIALIZER_CLASS_CONFIG to keyDeserializer.canonicalName,
            VALUE_DESERIALIZER_CLASS_CONFIG to valueDeserializer.canonicalName,
            GROUP_ID_CONFIG to groupId,
        ) + configOverrides
    )

    init {
        KafkaClientMetrics(consumer).bindTo(Metrics.meterRegistry)
        consumer.subscribe(listOf(topic))
        if (seekToBeginning) {
            seekToBeginningOnAssignment()
        }
    }

    private val log = logger()

    private val retriesPerPartition = ConcurrentHashMap<Int, AtomicInteger>()

    private val resumeQueue = ConcurrentLinkedQueue<TopicPartition>()

    private val retryTimer = Timer()

    private val replayer = PeriodicReplayer(
        consumer,
        isBigLeap = { t -> t.hour == 5 && t.minute == 0 },
        isSmallLeap = { t -> t.minute == 0 },
        bigLeap = 4_000,
        smallLeap = 100,
        enabled = replayPeriodically,
    )

    suspend fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        body: suspend (ConsumerRecord<K, V>) -> Unit
    ) = withContext(kafkaContext) {
        consumer.use {
            try {
                while (isActive && !stop.get() && !Health.terminating) {
                    replayer.replayWhenLeap()
                    consumer.resume(resumeQueue.pollAll())

                    val records = try {
                        consumer.poll(Duration.ofMillis(1000))
                    } catch (e: Exception) {
                        log.error("Unrecoverable error during poll {}", consumer.assignment(), e)
                        Health.subsystemAlive[Subsystem.KAFKA] = false
                        throw e
                    }

                    if (!records.isEmpty) {
                        val start = System.nanoTime()

                        try {
                            forEachRecord(records, body)
                        } finally {
                            val elapsed = System.nanoTime() - start
                            pollBodyTimer.record(elapsed, TimeUnit.NANOSECONDS)
                        }
                    }
                }
            } finally { // auto close via consumer.use to avoid rebalance storm
                log.info(
                    "stop signal received, closing consumer. Health.terminating={}, stop={}, isActive={}",
                    Health.terminating, stop.get(), isActive
                )
            }
        }

    }

    private suspend fun forEachRecord(
        records: ConsumerRecords<K, V>,
        body: suspend (ConsumerRecord<K, V>) -> Unit
    ) {
        if (records.isEmpty) {
            return
        }

        records.partitions().forEach currentPartition@{ partition ->
            val retries = retriesForPartition(partition.partition())
            records.records(partition).forEach currentRecord@{ record ->
                try {
                    if (!seekToBeginning) log.info("processing {}", record.loggableToString())
                    iterationEntryCounter.increment()
                    withContext(Dispatchers.IO) {
                        body(record)
                    }
                    consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
                    iterationExitCounter.increment()
                    if (!seekToBeginning) log.info("successfully processed {}", record.loggableToString())
                    retries.set(0)
                    return@currentRecord
                } catch (e: Exception) {
                    val attempt = retries.incrementAndGet()
                    val backoffMillis = capWithinMaxPollInterval(1000L * 2.toThePowerOf(attempt))
                    // log error if attempt is > 3 else just warn
                    log.atLevel(
                        if (attempt > 3)
                            Level.ERROR
                        else
                            Level.WARN
                    ).log(
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

    /**
     * make sure our backoff never exceeds max.poll.interval.ms - 5 seconds (or at least 1 second)
     * so that Kafka doesn't consider us dead
     */
    private fun capWithinMaxPollInterval(backoffMs: Long) = min(
        backoffMs,
        (MAX_POLL_INTERVAL_MS.toLong() - 5_000).coerceAtLeast(1_000)
    )

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

    private fun seekToBeginningOnAssignment() {
        consumer.subscribe(
            consumer.subscription(),
            object: ConsumerRebalanceListener {
                override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) {
                    consumer.endOffsets(partitions).forEach { (partition, endOffset) ->
                        onPartitionAssigned?.invoke(partition, endOffset - 1)
                    }

                    consumer.seekToBeginning(partitions.orEmpty())
                }
                override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) {
                    partitions?.forEach { partition ->
                        onPartitionRevoked?.invoke(partition)
                    }
                }
            }
        )
    }
}

private fun <T> ConcurrentLinkedQueue<T>.pollAll(): List<T> =
    generateSequence {
        this.poll()
    }.toList()

internal fun <K, V> ConsumerRecord<K, V>.loggableToString() = """
        |ConsumerRecord(
        |    topic = ${topic()},
        |    partition = ${partition()}, 
        |    offset = ${offset()},
        |    timestamp = ${timestamp()},
        |    key = ${key()}
        |    value = ${loggableValue()})
    """.trimMargin()

private fun <K, V> ConsumerRecord<K, V>.loggableValue() : String {
    return when (val value = value()) {
        null -> "Tombstone"
        is HendelseModel.Hendelse -> """
            |Hendelse(
            |    type = ${value.javaClass.simpleName},
            |    hendelseId = ${value.hendelseId},
            |    aggregateId = ${value.aggregateId},
            |    produsentId = ${value.produsentId},
            |    kildeAppNavn = ${value.kildeAppNavn})
        """.trimMargin()

        else -> value::class.java.simpleName
    }
}