package no.nav.arbeidsgiver.notifikasjon.hendelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import java.util.*

interface HendelseProdusent {
    suspend fun send(hendelse: Hendelse) {
        sendOgHentMetadata(hendelse)
    }
    suspend fun sendOgHentMetadata(hendelse: Hendelse): HendelseModel.HendelseMetadata
    suspend fun tombstone(key: UUID, orgnr: String)
}