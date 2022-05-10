package no.nav.arbeidsgiver.notifikasjon.hendelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata

interface Hendelsesstrøm {
    suspend fun forEach(body: suspend (Hendelse, HendelseMetadata) -> Unit)

    suspend fun forEach(body: suspend (Hendelse) -> Unit) {
        forEach{ hendelse: Hendelse, _: HendelseMetadata ->
            body(hendelse)
        }
    }
}