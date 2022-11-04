package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

class PartitionAwareHendelsesstrøm<PartitionState: Any>(
    groupId: String,
    replayPeriodically: Boolean = false,
    configure: Properties.() -> Unit = {},
    val initState: () -> PartitionState,
    val processEvent: suspend (state: PartitionState, event: HendelseModel.Hendelse) -> Unit,
    val processingLoopAfterCatchup: suspend (state: PartitionState) -> Unit,
) {
    class PartitionInfo<PartitionState: Any>(
        val state: PartitionState,
        val endOffsetAtAssignment: Long,
        var processingJob: Job? = null,
    )
    private val timer = Timer.builder("kafka.partition.catchup").register(Metrics.meterRegistry)

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

            processEvent(p.state, consumerRecord.value())

            if (p.processingJob == null && p.endOffsetAtAssignment <= consumerRecord.offset()) {
                p.processingJob = processingScope.launch {
                    processingLoopAfterCatchup(p.state)
                }
            }
        }
    }
}

