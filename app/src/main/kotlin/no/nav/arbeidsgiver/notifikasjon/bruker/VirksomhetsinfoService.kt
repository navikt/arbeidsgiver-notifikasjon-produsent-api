package no.nav.arbeidsgiver.notifikasjon.bruker

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret.Underenhet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.cache.getAsync
import java.util.concurrent.CompletableFuture

class VirksomhetsinfoService(
    val enhetsregisteret: Enhetsregisteret,
) {

    private val cache = Caffeine.newBuilder()
        .maximumSize(600_000)
        .recordStats()
        .buildAsync<String, Underenhet>().also {
            CaffeineCacheMetrics(
                it.synchronous(),
                "virksomhetsinfo",
                emptyList()
            ).bindTo(Metrics.meterRegistry)
        }

    suspend fun hentUnderenhet(virksomhetsnummer: String): Underenhet =
        cache.getAsync(virksomhetsnummer) {
            enhetsregisteret.hentUnderenhet(virksomhetsnummer)
        }

    fun cachePut(orgnr: String, navn: String) {
        cache.put(
            orgnr, CompletableFuture.completedFuture(
                Underenhet(
                    navn = navn,
                    organisasjonsnummer = orgnr,
                )
            )
        )
    }
}
