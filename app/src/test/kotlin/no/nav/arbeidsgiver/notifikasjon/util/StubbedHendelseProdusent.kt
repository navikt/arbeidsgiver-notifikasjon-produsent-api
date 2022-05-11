package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import java.util.*

class StubbedHendelseProdusent: HendelseProdusent {
    val hendelser = mutableListOf<HendelseModel.Hendelse>()

    inline fun <reified T> hendelserOfType() = hendelser.filterIsInstance<T>()

    override suspend fun send(hendelse: HendelseModel.Hendelse) {
        hendelser.add(hendelse)
    }

    override suspend fun tombstone(key: UUID, orgnr: String) {
        hendelser.removeIf {
            it.hendelseId == key
        }
    }
}