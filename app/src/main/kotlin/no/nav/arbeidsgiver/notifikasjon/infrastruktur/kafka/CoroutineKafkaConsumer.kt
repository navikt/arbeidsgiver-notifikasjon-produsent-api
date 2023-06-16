package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.toThePowerOf
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.Deserializer
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule

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
    private val configure: Properties.() -> Unit = {},
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
            configure: Properties.() -> Unit = {},
        ): CoroutineKafkaConsumer<K, V> = CoroutineKafkaConsumer(
            topic,
            groupId,
            keyDeserializer,
            valueDeserializer,
            seekToBeginning,
            replayPeriodically,
            onPartitionAssigned,
            onPartitionRevoked,
            configure
        )
    }

    private val properties = Properties().apply {
        putAll(CONSUMER_PROPERTIES)
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = keyDeserializer.canonicalName
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = valueDeserializer.canonicalName
        this[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        configure()
    }

    private val consumer: Consumer<K, V> = KafkaConsumer(properties)

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
        isBigLeap = basedOnEnv(
            prod = {{ t -> t.hour == 5 && t.minute == 0 }},
            other = {{ t -> t.hour % 2 == 0  && t.minute == 0 }}
        ),
        isSmallLeap = { t -> t.minute == 0 },
        bigLeap = 10_000,
        smallLeap = 100,
        enabled = replayPeriodically,
    )

    suspend fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        body: suspend (ConsumerRecord<K, V>) -> Unit
    ) {
        withContext(kafkaContext) {
            while (!stop.get() && !Health.terminating) {
                replayer.replayWhenLeap()
                consumer.resume(resumeQueue.pollAll())
                val records = try {
                    consumer.poll(Duration.ofMillis(1000))
                } catch (e: Exception) {
                    log.error("Unrecoverable error during poll {}", consumer.assignment(), e)
                    throw e
                }

                pollBodyTimer.record(Runnable {
                    forEachRecord(records, body)
                })
            }

        }
    }

    private fun forEachRecord(
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
                    runBlocking(Dispatchers.IO) {
                        body(record)
                    }
                    consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
                    iterationExitCounter.increment()
                    if (!seekToBeginning) log.info("successfully processed {}", record.loggableToString())
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

        else -> value!!::class.java.simpleName
    }
}