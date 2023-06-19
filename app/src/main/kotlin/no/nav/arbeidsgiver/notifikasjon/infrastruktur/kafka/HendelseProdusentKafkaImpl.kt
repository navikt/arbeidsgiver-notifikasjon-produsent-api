package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.OrgnrPartitioner.Companion.partitionOfOrgnr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.*

fun lagKafkaHendelseProdusent(
    topic: String = NOTIFIKASJON_TOPIC,
    configure: Properties.() -> Unit = {},
): HendelseProdusent {
    val properties = Properties().apply {
        putAll(PRODUCER_PROPERTIES)
        configure()
    }
    val kafkaProducer = KafkaProducer<KafkaKey, Hendelse>(properties)
    KafkaClientMetrics(kafkaProducer).bindTo(Metrics.meterRegistry)
    return HendelseProdusentKafkaImpl(kafkaProducer, topic)
}

private class HendelseProdusentKafkaImpl(
    private val producer: Producer<KafkaKey, Hendelse?>,
    private val topic: String,
) : HendelseProdusent {
    override suspend fun sendOgHentMetadata(hendelse: Hendelse) : HendelseModel.HendelseMetadata {
        val metadata = producer.suspendingSend(
            ProducerRecord(
                topic,
                hendelse.hendelseId.toString(),
                hendelse
            )
        )
        return HendelseModel.HendelseMetadata.fromKafkaTimestamp(metadata.timestamp())
    }

    override suspend fun tombstone(key: UUID, orgnr: String) {
        producer.suspendingSend(
            ProducerRecord(
                topic,
                partitionOfOrgnr(orgnr, producer.partitionsFor(topic).size),
                key.toString(),
                null
            )
        )
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <K : Any?, V : Any?> Producer<K, V>.suspendingSend(record: ProducerRecord<K, V>): RecordMetadata =
    suspendCancellableCoroutine { continuation ->
        val result = send(record) { recordMetadata, exception ->
            if (exception != null) {
                Health.subsystemAlive[Subsystem.KAFKA] = false
                continuation.cancel(exception)
            } else {
                continuation.resume(recordMetadata) {
                    /* nothing to close if canceled */
                }
            }
        }
        continuation.invokeOnCancellation {
            result.cancel(true)
        }
    }
