package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import org.apache.kafka.clients.producer.ProducerRecord

class StubbedKafkaProducer: CoroutineKafkaProducer<KafkaKey, HendelseModel.Hendelse> {
    val records = mutableListOf<ProducerRecord<KafkaKey, HendelseModel.Hendelse>>()
    val hendelser: List<HendelseModel.Hendelse>
        get() = records.map(ProducerRecord<KafkaKey, HendelseModel.Hendelse>::value)

    inline fun <reified T> hendelserOfType() = hendelser.filterIsInstance<T>()

    override suspend fun send(record: ProducerRecord<KafkaKey, HendelseModel.Hendelse>) {
        records.add(record)
    }

    override suspend fun tombstone(key: KafkaKey, orgnr: String) {
        records.removeIf {
            it.key() == key
        }
    }
}