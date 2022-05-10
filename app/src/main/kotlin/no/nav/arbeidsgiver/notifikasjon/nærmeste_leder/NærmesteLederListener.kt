package no.nav.arbeidsgiver.notifikasjon.nærmeste_leder

import no.nav.arbeidsgiver.notifikasjon.bruker.NærmesteLederModel

interface NærmesteLederListener {
    suspend fun forEach(body: suspend (NærmesteLederModel.NarmesteLederLeesah) -> Unit)
}