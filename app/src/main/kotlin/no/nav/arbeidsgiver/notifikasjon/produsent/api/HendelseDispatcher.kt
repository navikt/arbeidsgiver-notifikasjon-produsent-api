package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository

internal class HendelseDispatcher(
    private val kafkaProducer: HendelseProdusent,
    private val produsentRepository: ProdusentRepository,
) {

    internal suspend fun send(vararg hendelser: HendelseModel.Hendelse) {
        val metadatas = ArrayList<HendelseModel.HendelseMetadata>()
        for (hendelse in hendelser) {
            metadatas.add(kafkaProducer.sendOgHentMetadata(hendelse))
        }
        for ((hendelse, metadata) in hendelser.zip(metadatas)) {
            produsentRepository.oppdaterModellEtterHendelse(hendelse, metadata)
        }
    }
}