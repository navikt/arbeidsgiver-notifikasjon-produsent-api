package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.LongTaskTimer.Sample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

class PartitionAwareHendelsesstrøm<PartitionState: Any>(
    groupId: String,
    replayPeriodically: Boolean = false,
    configure: Properties.() -> Unit = {},
    private val initState: () -> PartitionState,
    private val processEvent: suspend (state: PartitionState, event: HendelseModel.Hendelse) -> Unit,
    private val processingLoopAfterCatchup: suspend (state: PartitionState) -> Unit,
) {
    private val log = logger()

    class PartitionInfo<PartitionState: Any>(
        val state: PartitionState,
        val endOffsetAtAssignment: Long,
        var catchupTimerSample: Sample?,
        var processingJob: Job? = null,
    )

    private val catchupTimer = LongTaskTimer
        .builder("kafka.partition.replay.ajour")
        .description("""
            How long it takes in PartitionAwareHendelsesstrøm to read a partition. 
            It reads partitions from then beginning, and detects when it reached the offset 
            that was "endOffset" at time of assignment.
        """)
        .register(Metrics.meterRegistry)

    private val partitionInfo: MutableMap<TopicPartition, PartitionInfo<PartitionState>> = HashMap()

    private val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val kafkaConsumer = CoroutineKafkaConsumer.new(
        topic = NOTIFIKASJON_TOPIC,
        groupId = groupId,
        keyDeserializer = StringDeserializer::class.java,
        valueDeserializer = ValueDeserializer::class.java,
        seekToBeginning = true,
        replayPeriodically = replayPeriodically,
        configure = configure,
        onPartitionAssigned = { partition: TopicPartition, endOffset: Long ->
            partitionInfo[partition] = PartitionInfo(
                state = initState(),
                endOffsetAtAssignment = endOffset,
                catchupTimerSample = catchupTimer.start(),
            )
        },
        onPartitionRevoked = { partition: TopicPartition ->
            val p = partitionInfo.remove(partition)
            p?.processingJob?.cancel()
        }
    )

    suspend fun start() {
        kafkaConsumer.forEach { consumerRecord ->
            val partition = TopicPartition(consumerRecord.topic(), consumerRecord.partition())
            val p = partitionInfo[partition]
                ?: error("missing partition information for received record")

            val value = consumerRecord.value()
            if (value != null) {
                processEvent(p.state, value)
            }

            if (p.processingJob == null && p.endOffsetAtAssignment <= consumerRecord.offset()) {
                p.catchupTimerSample?.stop()
                p.catchupTimerSample = null
                p.processingJob = processingScope.launch {
                    log.info("launching processingLoopAfterCatchup for $partition")
                    processingLoopAfterCatchup(p.state)
                }
            }
        }
    }
}

