package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class CoroutineKafkaConsumerCommitTest {

    private val topic = "test.topic"
    private val tp = TopicPartition(topic, 0)

    private fun mockConsumer(commitThrows: Boolean): MockConsumer<String, String> {
        val consumer = object : MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
            override fun commitSync(offsets: Map<TopicPartition, OffsetAndMetadata>, timeout: Duration) {
                if (commitThrows) throw TimeoutException("Timeout of ${timeout.toMillis()}ms expired")
                super.commitSync(offsets, timeout)
            }
        }
        consumer.subscribe(listOf(topic))
        consumer.rebalance(listOf(tp))
        consumer.updateBeginningOffsets(mapOf(tp to 0L))
        consumer.seek(tp, 0L)
        return consumer
    }

    private fun MockConsumer<String, String>.scheduleRecords(count: Int, stop: AtomicBoolean) {
        repeat(count) { i ->
            schedulePollTask {
                addRecord(ConsumerRecord(topic, 0, i.toLong(), "key", "value-$i"))
            }
        }
        schedulePollTask { stop.set(true) }
    }

    @Test
    fun `commit-feil under terskel restarter ikke - KAFKA forblir true`() = runBlocking {
        var kafkaHealty = true
        val consumer = mockConsumer(commitThrows = true)
        val stop = AtomicBoolean(false)
        consumer.scheduleRecords(count = COMMIT_FAILURE_THRESHOLD - 1, stop = stop)

        CoroutineKafkaConsumer.forTest(consumer, onKafkaUnhealthy = { kafkaHealty = false }).forEach(stop = stop) { /* body ok */ }

        assertTrue(kafkaHealty)
    }

    @Test
    fun `vedvarende commit-feil over terskel flagger KAFKA unhealthy og avslutter loopen`() = runBlocking {
        var kafkaHealty = true
        val consumer = mockConsumer(commitThrows = true)
        val stop = AtomicBoolean(false)
        // Flere records enn terskelen; loopen skal avslutte via Health.terminating før stop trigges.
        consumer.scheduleRecords(count = COMMIT_FAILURE_THRESHOLD + 2, stop = stop)

        CoroutineKafkaConsumer.forTest(consumer, onKafkaUnhealthy = { kafkaHealty = false }).forEach(stop = stop) { /* body ok */ }

        assertFalse(kafkaHealty)
    }

    @Test
    fun `poison-pill i body pauser partisjonen og lar KAFKA forbli true`() = runBlocking {
        var kafkaHealty = true
        val consumer = mockConsumer(commitThrows = false)
        val stop = AtomicBoolean(false)
        consumer.scheduleRecords(count = COMMIT_FAILURE_THRESHOLD + 1, stop = stop)

        CoroutineKafkaConsumer.forTest(consumer, onKafkaUnhealthy = { kafkaHealty = false }).forEach(stop = stop) {
            throw RuntimeException("poison pill")
        }

        assertTrue(kafkaHealty)
        // partisjonen ble pauset av backoff-stien (ikke flagget unhealthy)
        assertTrue(consumer.paused().contains(tp))
    }
}



/**
 * Test-seam: bygg en konsument rundt en injisert [Consumer] (typisk en MockConsumer),
 * slik at commit-/rebalance-stiene kan testes uten en ekte Kafka-broker.
 */
private fun CoroutineKafkaConsumer.Companion.forTest(
    consumer: Consumer<String, String>,
    topic: String = "test.topic",
    groupId: String = "test-group",
    onKafkaUnhealthy: () -> Unit,
): CoroutineKafkaConsumer<String, String> = CoroutineKafkaConsumer.new(
    topic = topic,
    groupId = groupId,
    keyDeserializer = org.apache.kafka.common.serialization.StringDeserializer::class.java,
    valueDeserializer = org.apache.kafka.common.serialization.StringDeserializer::class.java,
    onPartitionAssigned = null,
    onPartitionRevoked = null,
    onKafkaUnhealthy = onKafkaUnhealthy,
    consumerOverride = consumer,
)
