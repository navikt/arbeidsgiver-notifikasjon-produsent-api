package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret.Underenhet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.SimpleLRUCache

class VirksomhetsinfoService(
    enhetsregisteret: Enhetsregisteret,
) {
    private val cache = SimpleLRUCache<String, Underenhet>(100_000) { orgnr ->
        enhetsregisteret.hentUnderenhet(orgnr)
    }

    suspend fun hentUnderenhet(virksomhetsnummer: String): Underenhet =
        cache.get(virksomhetsnummer)
}