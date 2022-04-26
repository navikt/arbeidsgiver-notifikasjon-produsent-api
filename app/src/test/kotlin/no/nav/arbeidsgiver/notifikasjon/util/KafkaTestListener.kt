package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.*
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.concurrent.atomic.AtomicInteger

fun TestConfiguration.kafka(): TestKafka =
    KafkaTestListener()
        .also{ listener(it) }

interface TestKafka {
    fun newConsumer(): CoroutineKafkaConsumer<KafkaKey, Hendelse>
    fun newProducer(): CoroutineKafkaProducer<KafkaKey, Hendelse>
}

class KafkaTestListener: TestListener, TestKafka {
    private val admin: Admin
        get() = Admin.create(
            mapOf(
                BOOTSTRAP_SERVERS_CONFIG to COMMON_PROPERTIES[BOOTSTRAP_SERVERS_CONFIG]
            )
        )
    override val name: String
        get() = "KafkaTestListener"

    override suspend fun beforeSpec(spec: Spec) {
        val topic = "test-${topicCounter.incrementAndGet()}"
        val result = admin.createTopics(listOf(NewTopic(topic, 1, 1)))
        val future = result.values()[topic]!!
        while (!future.isDone) {
            delay(100)
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        //env.tearDown()
    }

    private val groupIdCounter = AtomicInteger(0)
    private val topicCounter = AtomicInteger(0)

    override fun newConsumer() =
        createKafkaConsumer {
            this[ConsumerConfig.GROUP_ID_CONFIG] = "test-" + groupIdCounter.getAndIncrement()
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
            this[BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }

    override fun newProducer() =
        createKafkaProducer {
            this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
            this[BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }
}

fun KafkaEnvironment.bootstrapServers() : String {
    return brokers.joinToString(separator = ",") { "${it.host}:${it.port}" }
}