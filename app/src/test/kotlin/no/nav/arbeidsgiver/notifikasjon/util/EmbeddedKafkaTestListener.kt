package no.nav.arbeidsgiver.notifikasjon.util

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.hendelse.Hendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.*
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.RawKafkaReader
import no.nav.arbeidsgiver.notifikasjon.kafka_backup.RawKafkaReaderImpl
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import java.util.concurrent.atomic.AtomicInteger

fun TestConfiguration.embeddedKafka(): EmbeddedKafka =
    EmbeddedKafkaTestListener()
        .also { listener(it) }

interface EmbeddedKafka {
    fun newConsumer(): Hendelsesstrøm
    fun newRawConsumer(): RawKafkaReader
    fun newProducer(): HendelseProdusent
}

class EmbeddedKafkaTestListener: TestListener, EmbeddedKafka {
    private val env: KafkaEnvironment = KafkaEnvironment(topicNames = listOf(NOTIFIKASJON_TOPIC))

    override suspend fun beforeSpec(spec: Spec) {
        env.start()
        while (env.serverPark.brokerStatus !is KafkaEnvironment.BrokerStatus.Available) {
            println("kafka server not ready yet: ${env.serverPark.brokerStatus}")
            delay(100)
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        env.tearDown()
    }

    private var groupIdCounter = AtomicInteger(0)

    override fun newConsumer() =
        HendelsesstrømKafkaImpl(
            groupId = "test-" + groupIdCounter.getAndIncrement(),
        ) {
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }

    override fun newRawConsumer() = RawKafkaReaderImpl(
        topic = NOTIFIKASJON_TOPIC,
        groupId = "test-" + groupIdCounter.getAndIncrement()
    ) {
        this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
        this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
    }

    override fun newProducer() =
        lagKafkaHendelseProdusent {
            this[CommonClientConfigs.RECONNECT_BACKOFF_MAX_MS_CONFIG] = 15000
            this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = env.bootstrapServers()
        }
}

fun KafkaEnvironment.bootstrapServers() : String {
    return brokers.joinToString(separator = ",") { "${it.host}:${it.port}" }
}