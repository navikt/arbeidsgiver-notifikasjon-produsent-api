package no.nav.arbeidsgiver.notifikasjon.nærmeste_leder

interface NærmesteLederListener {
    suspend fun forEach(body: suspend (NærmesteLederModel.NarmesteLederLeesah) -> Unit)
}