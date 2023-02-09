package no.nav.arbeidsgiver.notifikasjon.hendelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import java.util.concurrent.atomic.AtomicBoolean

interface Hendelsesstrøm {
    suspend fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        body: suspend (Hendelse, HendelseMetadata) -> Unit
    )

    suspend fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        body: suspend (Hendelse) -> Unit
    ) {
        forEach(stop) { hendelse: Hendelse, _: HendelseMetadata ->
            body(hendelse)
        }
    }
}