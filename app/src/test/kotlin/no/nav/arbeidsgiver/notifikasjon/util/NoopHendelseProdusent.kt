package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import java.time.Instant
import java.util.*

object NoopHendelseProdusent : HendelseProdusent {
    override suspend fun send(hendelse: HendelseModel.Hendelse) = Unit
    override suspend fun sendOgHentMetadata(hendelse: HendelseModel.Hendelse): HendelseModel.HendelseMetadata =
        HendelseModel.HendelseMetadata(
            Instant.parse("1970-01-01T00:00:00Z")
        )
    override suspend fun tombstone(key: UUID, orgnr: String) = Unit
}