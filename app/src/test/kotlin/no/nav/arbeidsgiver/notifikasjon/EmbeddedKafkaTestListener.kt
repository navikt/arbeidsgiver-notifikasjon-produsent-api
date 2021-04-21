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
import kotlin.reflect.KProperty

class EmbeddedKafkaTestListener: TestListener {
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

object EmbeddedKafka {
    val env: KafkaEnvironment by KafkaEnvironmentDelegate()
    val producer: Producer<KafkaKey, Event> by ProducerDelegate()
    val consumer: Consumer<KafkaKey, Event> by ConsumerDelegate()
}
class KafkaEnvironmentDelegate {
    private var kafkaEnv: KafkaEnvironment = KafkaEnvironment(topicNames = listOf("arbeidsgiver.notifikasjon"))
    operator fun getValue(thisRef: Any?, property: KProperty<*>): KafkaEnvironment {
        return kafkaEnv
    }
}
class ProducerDelegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Producer<KafkaKey, Event> {
        return createKafkaProducer {
            this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = EmbeddedKafka.env.bootstrapServers()
        }
    }
}
class ConsumerDelegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Consumer<KafkaKey, Event> {
        return createKafkaConsumer {
            this[ConsumerConfig.GROUP_ID_CONFIG] = "test-" + Instant.now()
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = EmbeddedKafka.env.bootstrapServers()
        }
    }
}
fun KafkaEnvironment.bootstrapServers() : String {
    return brokers.joinToString(separator = ",") { "${it.host}:${it.port}" }
}