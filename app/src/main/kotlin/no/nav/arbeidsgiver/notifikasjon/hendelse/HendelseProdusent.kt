package no.nav.arbeidsgiver.notifikasjon.hendelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import java.util.*

interface HendelseProdusent {
    suspend fun send(hendelse: Hendelse)
    suspend fun tombstone(key: UUID, orgnr: String)
}