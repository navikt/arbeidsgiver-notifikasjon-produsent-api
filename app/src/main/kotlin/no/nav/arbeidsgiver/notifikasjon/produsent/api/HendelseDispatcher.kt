package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository

internal class HendelseDispatcher(
    private val kafkaProducer: HendelseProdusent,
    private val produsentRepository: ProdusentRepository,
) {

    internal suspend fun send(vararg hendelser: HendelseModel.Hendelse) {
        for (hendelse in hendelser) {
            kafkaProducer.send(hendelse)
        }
        for (hendelse in hendelser) {
            produsentRepository.oppdaterModellEtterHendelse(hendelse)
        }
    }
}