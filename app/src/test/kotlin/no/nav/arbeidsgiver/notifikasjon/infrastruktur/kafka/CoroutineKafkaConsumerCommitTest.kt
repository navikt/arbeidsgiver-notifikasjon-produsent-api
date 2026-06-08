package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class CoroutineKafkaConsumerCommitTest {

    private val topic = "test.topic"
    private val tp = TopicPartition(topic, 0)

    @AfterEach
    fun resetHealth() {
        // Health er en global singleton; sett KAFKA tilbake til true så andre tester ikke påvirkes.
        Health.subsystemAlive[Subsystem.KAFKA] = true
    }

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
        Health.subsystemAlive[Subsystem.KAFKA] = true
        val consumer = mockConsumer(commitThrows = true)
        val stop = AtomicBoolean(false)
        consumer.scheduleRecords(count = COMMIT_FAILURE_THRESHOLD - 1, stop = stop)

        CoroutineKafkaConsumer.forTest(consumer).forEach(stop = stop) { /* body ok */ }

        assertEquals(true, Health.subsystemAlive[Subsystem.KAFKA])
    }

    @Test
    fun `vedvarende commit-feil over terskel flagger KAFKA unhealthy og avslutter loopen`() = runBlocking {
        Health.subsystemAlive[Subsystem.KAFKA] = true
        val consumer = mockConsumer(commitThrows = true)
        val stop = AtomicBoolean(false)
        // Flere records enn terskelen; loopen skal avslutte via Health.terminating før stop trigges.
        consumer.scheduleRecords(count = COMMIT_FAILURE_THRESHOLD + 2, stop = stop)

        CoroutineKafkaConsumer.forTest(consumer).forEach(stop = stop) { /* body ok */ }

        assertEquals(false, Health.subsystemAlive[Subsystem.KAFKA])
    }

    @Test
    fun `poison-pill i body pauser partisjonen og lar KAFKA forbli true`() = runBlocking {
        Health.subsystemAlive[Subsystem.KAFKA] = true
        val consumer = mockConsumer(commitThrows = false)
        val stop = AtomicBoolean(false)
        consumer.scheduleRecords(count = 1, stop = stop)

        CoroutineKafkaConsumer.forTest(consumer).forEach(stop = stop) {
            throw RuntimeException("poison pill")
        }

        assertEquals(true, Health.subsystemAlive[Subsystem.KAFKA])
        // partisjonen ble pauset av backoff-stien (ikke flagget unhealthy)
        assertTrue(consumer.paused().contains(tp))
    }
}
