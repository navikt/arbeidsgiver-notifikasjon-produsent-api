package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
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
    private val stateForPartition: MutableMap<Int, PartitionState> = HashMap()
    private val endOffsetAtAssignment: MutableMap<Int, Long> = HashMap()
    private val jobForPartition: MutableMap<Int, Job> = HashMap()

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
            endOffsetAtAssignment[partition.partition()] = endOffset
            stateForPartition[partition.partition()] = initState()
        },
        onPartitionRevoked = { partition: TopicPartition ->
            stateForPartition.remove(partition.partition())
            jobForPartition[partition.partition()]?.cancel()
        }
    )

    suspend fun start() {
        kafkaConsumer.forEach { consumerRecord ->
            val partition = consumerRecord.partition()
            val state = stateForPartition[partition] !! /* TODO: why did we get event for this partition? */
            processEvent(state, consumerRecord.value())

            val maxOffset = endOffsetAtAssignment[partition]!! /* TODO: why don't we have max offset for this partiton */
            if (maxOffset <= consumerRecord.offset()) {
                jobForPartition[partition] = processingScope.launch {
                    processingLoopAfterCatchup(state)
                }
            }
        }
    }
}
