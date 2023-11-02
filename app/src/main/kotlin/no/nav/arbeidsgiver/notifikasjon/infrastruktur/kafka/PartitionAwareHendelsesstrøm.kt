package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.LongTaskTimer.Sample
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

interface PartitionProcessor: AutoCloseable {
    suspend fun processHendelse(hendelse: HendelseModel.Hendelse)
    suspend fun processingLoopStep()
}

class PartitionAwareHendelsesstrøm(
    groupId: String,
    replayPeriodically: Boolean = false,
    configure: Properties.() -> Unit = {},
    private val newPartitionProcessor: () -> PartitionProcessor,
) {
    private val catchupTimer = LongTaskTimer
        .builder("kafka.partition.replay.ajour")
        .description("""
            How long it takes in PartitionAwareHendelsesstrøm to read a partition. 
            It reads partitions from then beginning, and detects when it reached the offset 
            that was "endOffset" at time of assignment.
        """)
        .register(Metrics.meterRegistry)

    private val partitionProcessors: MutableMap<TopicPartition, PartitionProcessorState> = ConcurrentHashMap()

    private val kafkaConsumer = CoroutineKafkaConsumer.new(
        topic = NOTIFIKASJON_TOPIC,
        groupId = groupId,
        keyDeserializer = StringDeserializer::class.java,
        valueDeserializer = ValueDeserializer::class.java,
        seekToBeginning = true,
        replayPeriodically = replayPeriodically,
        configure = configure,
        onPartitionAssigned = { partition: TopicPartition, maxOffset: Long ->
            partitionProcessors[partition] = PartitionProcessorState(
                partitionProcessor = newPartitionProcessor(),
                partition = partition,
                maxOffsetAtAssignment = maxOffset,
                catchupTimerSample = catchupTimer.start(),
            )
        },
        onPartitionRevoked = { partition: TopicPartition ->
            partitionProcessors.remove(partition)?.close()
        }
    )

    suspend fun start() {
        kafkaConsumer.forEach { consumerRecord ->
            val partition = TopicPartition(consumerRecord.topic(), consumerRecord.partition())
            val partitionProcessor = partitionProcessors[partition]
                ?: error("missing partition information for received record")
            partitionProcessor.processRecord(consumerRecord)
        }
    }
}

private class PartitionProcessorState(
    private val partitionProcessor: PartitionProcessor,
    private val maxOffsetAtAssignment: Long,
    private val partition: TopicPartition,
    private var catchupTimerSample: Sample?,
    private var processingThread: Thread? = null,
) {
    private val log = logger()

    @Volatile
    private var partitionAssigned = true

    suspend fun processRecord(consumerRecord: ConsumerRecord<String, HendelseModel.Hendelse>) {
        val value = consumerRecord.value()
        if (value != null) {
            partitionProcessor.processHendelse(value)
        }

        if (processingThread == null && maxOffsetAtAssignment <= consumerRecord.offset()) {
            catchupTimerSample?.stop()
            catchupTimerSample = null
            startProcessLoop()
        }
    }

    private fun startProcessLoop() {
        processingThread = Thread {
            log.info("starting processing loop for partition $partition")
            try {
                while (partitionAssigned) {
                    runBlocking {
                        partitionProcessor.processingLoopStep()
                    }
                }
                log.info("controlled shutdown of processing loop for partition $partition")
            } catch (e: Exception) {
                log.error("unexpected exception in processing loop for partition $partition.", e)
                Health.subsystemAlive[Subsystem.KAFKA] = false
            }
        }.also {
            it.start()
        }
    }

    fun close() {
        partitionAssigned = false
        catchupTimerSample?.stop()
        processingThread?.join()
        partitionProcessor.close()
    }
}
