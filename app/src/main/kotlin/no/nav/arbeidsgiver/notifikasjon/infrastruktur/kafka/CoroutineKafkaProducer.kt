package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.OrgnrPartitioner.Companion.partitionOfOrgnr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.*

interface CoroutineKafkaProducer<K, V> {
    suspend fun send(record: ProducerRecord<K, V>): RecordMetadata
    suspend fun tombstone(key: K, orgnr: String): RecordMetadata
}

fun createKafkaProducer(configure: Properties.() -> Unit = {}): CoroutineKafkaProducer<KafkaKey, Hendelse> {
    val properties = Properties().apply {
        putAll(PRODUCER_PROPERTIES)
        configure()
    }
    val kafkaProducer = KafkaProducer<KafkaKey, Hendelse>(properties)
    KafkaClientMetrics(kafkaProducer).bindTo(Metrics.meterRegistry)
    return CoroutineKafkaProducerImpl(kafkaProducer)
}

@JvmInline
value class CoroutineKafkaProducerImpl<K, V>(
    private val producer: Producer<K, V>
) : CoroutineKafkaProducer<K, V> {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun send(record: ProducerRecord<K, V>): RecordMetadata {
        return suspendCancellableCoroutine { continuation ->
            val result = producer.send(record) { metadata, exception ->
                if (exception != null) {
                    continuation.cancel(exception)
                } else {
                    continuation.resume(metadata) {
                        /* nothing to close if canceled */
                    }
                }
            }

            continuation.invokeOnCancellation {
                result.cancel(true)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun tombstone(key: K, orgnr: String): RecordMetadata {
        return send(
            ProducerRecord<K, V>(
                TOPIC,
                partitionOfOrgnr(orgnr, producer.partitionsFor(TOPIC).size),
                key,
                null
            )
        )
    }
}

suspend fun CoroutineKafkaProducer<KafkaKey, Hendelse>.sendHendelse(
    hendelse: Hendelse
) {
    this.sendHendelseMedKey(hendelse.hendelseId, hendelse)
}

suspend fun CoroutineKafkaProducer<KafkaKey, Hendelse>.sendHendelseMedKey(
    key: UUID,
    hendelse: Hendelse,
) {
    send(
        ProducerRecord(
            TOPIC,
            key.toString(),
            hendelse
        )
    )
}