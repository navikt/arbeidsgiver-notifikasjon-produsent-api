package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.mockk.mockk
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.hendelse.Event
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import java.time.Instant
import kotlin.reflect.KProperty


fun TestConfiguration.kafkaEnabled() : Boolean {
    return registeredListeners().any { it is EmbeddedKafkaListener }
}

fun TestConfiguration.kafkaEnv() : KafkaEnv {
    return if(kafkaEnabled()) EmbeddedKafka else MockedKafka
}

class EmbeddedKafkaListener : TestListener {
    override val name: String
        get() = "EmbeddedKafkaListener"

    override suspend fun beforeSpec(spec: Spec) {
        EmbeddedKafka.env.start()
        while (EmbeddedKafka.env.serverPark.brokerStatus !is KafkaEnvironment.BrokerStatus.Available) {
            delay(100)
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        EmbeddedKafka.env.tearDown()
    }
}

sealed class KafkaEnv {
    abstract val producer: KafkaProducer<KafkaKey, Event>
    abstract val consumer: KafkaConsumer<KafkaKey, Event>
}
object EmbeddedKafka : KafkaEnv() {
    val env: KafkaEnvironment by KafkaEnvironmentDelegate()
    override val producer: KafkaProducer<KafkaKey, Event> by KafkaProducerDelegate()
    override val consumer: KafkaConsumer<KafkaKey, Event> by KafkaConsumerDelegate()
}
object MockedKafka : KafkaEnv() {
    override val producer: KafkaProducer<KafkaKey, Event> = mockk(relaxed = true)
    override val consumer: KafkaConsumer<KafkaKey, Event> = mockk(relaxed = true)
}

class KafkaEnvironmentDelegate {
    private var kafkaEnv: KafkaEnvironment = KafkaEnvironment(topicNames = listOf("arbeidsgiver.notifikasjon"))
    operator fun getValue(thisRef: Any?, property: KProperty<*>): KafkaEnvironment {
        return kafkaEnv
    }
}
class KafkaProducerDelegate {
    private var producer: KafkaProducer<KafkaKey, Event> = createProducer {
        this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = EmbeddedKafka.env.bootstrapServers()
    }
    operator fun getValue(thisRef: Any?, property: KProperty<*>): KafkaProducer<KafkaKey, Event> {
        return producer
    }
}
class KafkaConsumerDelegate {
    private val consumer: KafkaConsumer<KafkaKey, Event> = createConsumer {
        this[ConsumerConfig.GROUP_ID_CONFIG] = "test-" + Instant.now()
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = EmbeddedKafka.env.bootstrapServers()
    }
    operator fun getValue(thisRef: Any?, property: KProperty<*>): KafkaConsumer<KafkaKey, Event> {
        return consumer
    }
}

fun KafkaEnvironment.bootstrapServers() : String {
    return brokers.joinToString(separator = ",") { "${it.host}:${it.port}" }
}