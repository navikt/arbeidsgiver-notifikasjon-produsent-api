package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.*
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.concurrent.atomic.AtomicInteger

fun TestConfiguration.embeddedKafka(): EmbeddedKafka =
    EmbeddedKafkaTestListener()
        .also{ listener(it) }

interface EmbeddedKafka {
    fun newConsumer(): CoroutineKafkaConsumer<KafkaKey, Hendelse>
    fun newProducer(): CoroutineKafkaProducer<KafkaKey, Hendelse>
}

class EmbeddedKafkaTestListener: TestListener, EmbeddedKafka {
    private val env: KafkaEnvironment = KafkaEnvironment(topicNames = listOf("arbeidsgiver.notifikasjon"))

    override val name: String
        get() = "EmbeddedKafkaListener"

    override suspend fun beforeSpec(spec: Spec) {
        env.start()
        while (env.serverPark.brokerStatus !is KafkaEnvironment.BrokerStatus.Available) {
            delay(100)
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        env.tearDown()
    }

    private var groupIdCounter = AtomicInteger(0)

    override fun newConsumer() =
        createKafkaConsumer {
            this[ConsumerConfig.GROUP_ID_CONFIG] = "test-" + groupIdCounter.getAndIncrement()
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }

    override fun newProducer() =
        createKafkaProducer {
            this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }
}

fun KafkaEnvironment.bootstrapServers() : String {
    return brokers.joinToString(separator = ",") { "${it.host}:${it.port}" }
}