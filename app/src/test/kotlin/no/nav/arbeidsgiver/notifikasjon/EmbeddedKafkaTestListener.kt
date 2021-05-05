package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.createKafkaConsumer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.createKafkaProducer
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.Producer
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KProperty

class EmbeddedKafkaTestListener: TestListener {
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

    var groupIdCounter = AtomicInteger(0)
    fun newConsumer() =
        createKafkaConsumer {
            this[ConsumerConfig.GROUP_ID_CONFIG] = "test-" + groupIdCounter.getAndIncrement()
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }

    fun newProducer() =
        createKafkaProducer {
            this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }
}

fun KafkaEnvironment.bootstrapServers() : String {
    return brokers.joinToString(separator = ",") { "${it.host}:${it.port}" }
}