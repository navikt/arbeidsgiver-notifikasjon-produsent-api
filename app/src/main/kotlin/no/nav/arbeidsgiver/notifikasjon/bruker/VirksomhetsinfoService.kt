package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret.Underenhet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.FunkyCache
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger

class VirksomhetsinfoService(
    enhetsregisteret: Enhetsregisteret,
) {
    private val log = logger()

    private val cache = FunkyCache<String, Underenhet>(600_000) { orgnr ->
        log.debug("oppslag ereg $orgnr")
        enhetsregisteret.hentUnderenhet(orgnr)
    }

    suspend fun hentUnderenhet(virksomhetsnummer: String): Underenhet =
        cache.get(virksomhetsnummer).also {
            log.debug("hentUnderenhet($virksomhetsnummer) -> $it")
        }

    suspend fun altinnObserver(altinnReportee: AltinnReportee) {
        log.debug("observe altinn $altinnReportee")
        val virksomhetsnummer = altinnReportee.organizationNumber ?: return
        cache.put(virksomhetsnummer, Underenhet(
            navn = altinnReportee.name,
            organisasjonsnummer = virksomhetsnummer,
        ))
    }
}