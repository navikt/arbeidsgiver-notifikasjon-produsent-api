package no.nav.arbeidsgiver.notifikasjon.hendelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

interface Hendelsesstrøm {
    suspend fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        onTombstone: suspend (UUID) -> Unit = skipTombstones,
        body: suspend (Hendelse, HendelseMetadata) -> Unit
    )
    suspend fun forEach(
        stop: AtomicBoolean = AtomicBoolean(false),
        onTombstone: suspend (UUID) -> Unit = skipTombstones,
        body: suspend (Hendelse) -> Unit
    ) {
        forEach(stop, onTombstone) { hendelse: Hendelse, _: HendelseMetadata ->
            body(hendelse)
        }
    }

    companion object {
        private val log = logger()
        private val skipTombstones: suspend (UUID) -> Unit = { log.info("skipping tombstoned event key=${it}") }
    }
}