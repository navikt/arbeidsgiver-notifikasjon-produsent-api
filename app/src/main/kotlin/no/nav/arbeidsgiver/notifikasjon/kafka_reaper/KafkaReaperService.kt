package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey

interface KafkaReaperService {
    fun håndterHendelse(hendelse: Hendelse)
}

class KafkaReaperServiceImpl(
    kafkaReaperModel: KafkaReaperModel,
    kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>
) : KafkaReaperService {

    override fun håndterHendelse(hendelse: Hendelse) {
        throw RuntimeException("TODO: Ikke implementert")
    }
}
