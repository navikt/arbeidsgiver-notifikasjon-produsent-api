package no.nav.arbeidsgiver.notifikasjon.hendelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import java.util.concurrent.atomic.AtomicBoolean

interface Hendelsesstrøm {
    fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        body: suspend (Hendelse, HendelseMetadata) -> Unit
    )

    fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        body: suspend (Hendelse) -> Unit
    ) {
        forEach(stop) { hendelse: Hendelse, _: HendelseMetadata ->
            body(hendelse)
        }
    }
}