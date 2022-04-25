package no.nav.arbeidsgiver.notifikasjon.util

import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

class StubbedKafkaProducer: CoroutineKafkaProducer<KafkaKey, HendelseModel.Hendelse> {
    val records = mutableListOf<ProducerRecord<KafkaKey, HendelseModel.Hendelse>>()
    val hendelser: List<HendelseModel.Hendelse>
        get() = records.map(ProducerRecord<KafkaKey, HendelseModel.Hendelse>::value)

    override suspend fun send(record: ProducerRecord<KafkaKey, HendelseModel.Hendelse>): RecordMetadata {
        records.add(record)
        return mockk(relaxed = true)
    }

    override suspend fun tombstone(key: KafkaKey, orgnr: String): RecordMetadata {
        records.removeIf {
            it.key() == key
        }
        return mockk(relaxed = true)
    }
}