package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import java.util.*

object NoopHendelseProdusent: HendelseProdusent {
    override suspend fun send(hendelse: HendelseModel.Hendelse) = Unit
    override suspend fun tombstone(key: UUID, orgnr: String) = Unit
}