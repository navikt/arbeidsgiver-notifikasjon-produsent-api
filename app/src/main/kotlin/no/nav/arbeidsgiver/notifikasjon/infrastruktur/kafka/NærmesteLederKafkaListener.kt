package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesahDeserializer
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NærmesteLederModel.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NærmesteLederListener
import org.apache.kafka.common.serialization.StringDeserializer

class NærmesteLederKafkaListener: NærmesteLederListener {
    private val consumer: CoroutineKafkaConsumer<String, NarmesteLederLeesah> = CoroutineKafkaConsumer.new(
        topic = "teamsykmelding.syfo-narmesteleder-leesah",
        groupId = "notifikasjon-bruker-api-narmesteleder-model-builder",
        keyDeserializer = StringDeserializer::class.java,
        valueDeserializer = NarmesteLederLeesahDeserializer::class.java,
    )

    override suspend fun forEach(body: suspend (NarmesteLederLeesah) -> Unit) {
        consumer.forEach { record ->
            val value = record.value()
            if (value != null) {
                body(record.value())
            }
        }
    }
}