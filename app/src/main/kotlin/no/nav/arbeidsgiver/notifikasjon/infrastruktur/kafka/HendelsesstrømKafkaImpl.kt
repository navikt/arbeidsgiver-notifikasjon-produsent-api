package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.hendelse.Hendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import org.apache.kafka.clients.consumer.ConsumerConfig

class HendelsesstrømKafkaImpl(
    groupId: String,
    seekToBeginning: Boolean = false,
): Hendelsesstrøm {
    private val consumer = CoroutineKafkaConsumer<KafkaKey, Hendelse>(
        topic = TOPIC,
        seekToBeginning = seekToBeginning,
    ) {
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    }

    override suspend fun forEach(body: suspend (Hendelse, HendelseMetadata) -> Unit) {
        consumer.forEachEvent { hendelse, metadata ->
            body(hendelse, metadata)
        }
    }
}



