package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.bruker.NarmesteLederLeesahDeserializer
import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NærmesteLederListener
import org.apache.kafka.clients.consumer.ConsumerConfig

class NærmesteLederKafkaListener: NærmesteLederListener {
    private val topic = "teamsykmelding.syfo-narmesteleder-leesah"
    private val groupId = "notifikasjon-bruker-api-narmesteleder-model-builder"
    private val deserializer = NarmesteLederLeesahDeserializer::class.java.canonicalName

    private val consumer = CoroutineKafkaConsumer<String, NarmesteLederLeesah>(topic) {
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer)
    }

    override suspend fun forEach(body: suspend (NarmesteLederLeesah) -> Unit) {
        consumer.forEachEvent { event, _ ->
            body(event)
        }
    }
}