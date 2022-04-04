package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository

class HendelseDispatcher(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, HendelseModel.Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {

    internal suspend fun send(vararg hendelser: HendelseModel.Hendelse) {
        for (hendelse in hendelser) {
            kafkaProducer.sendHendelse(hendelse)
        }
        for (hendelse in hendelser) {
            produsentRepository.oppdaterModellEtterHendelse(hendelse)
        }
    }
}