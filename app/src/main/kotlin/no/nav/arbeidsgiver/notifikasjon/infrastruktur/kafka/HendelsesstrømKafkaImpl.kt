package no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka

import no.nav.arbeidsgiver.notifikasjon.hendelse.Hendelsesstrøm
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class HendelsesstrømKafkaImpl(
    groupId: String,
    seekToBeginning: Boolean = false,
    configure: Properties.() -> Unit = {},
): Hendelsesstrøm {
    private val log = logger()

    private val consumer = CoroutineKafkaConsumer<KafkaKey, Hendelse>(
        topic = TOPIC,
        groupId = groupId,
        seekToBeginning = seekToBeginning,
        configure = configure,
    )

    override suspend fun forEach(
        stop: AtomicBoolean,
        body: suspend (Hendelse, HendelseMetadata) -> Unit
    ) {
        consumer.forEach(stop) { record ->
            val recordValue = record.value()
            if (recordValue == null) {
                log.info("skipping tombstoned event key=${record.key()}")
            } else {
                body(recordValue, HendelseMetadata(Instant.ofEpochMilli(record.timestamp())))
            }
        }
    }
}



