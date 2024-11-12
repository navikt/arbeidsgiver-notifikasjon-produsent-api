package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.Counter
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.cache.getAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.coRecord
import java.time.Duration

interface AltinnTilgangerService {
    suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
    ): AltinnTilganger
}

class AltinnTilgangerServiceImpl(
    private val altinnTilgangerClient: AltinnTilgangerClient
): AltinnTilgangerService {
    private val timer = Metrics.meterRegistry.timer("altinn_klient_hent_alle_tilganger")
    private val initiatedCounter = Counter.builder("altinn.rettigheter.lookup.initiated")
        .register(Metrics.meterRegistry)
    private val successCounter = Counter.builder("altinn.rettigheter.lookup.success")
        .register(Metrics.meterRegistry)
    private val failCounter = Counter.builder("altinn.rettigheter.lookup.fail")
        .register(Metrics.meterRegistry)

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .maximumSize(25_000)
        .buildAsync<String, AltinnTilganger>()

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String
    ): AltinnTilganger = timer.coRecord {
        initiatedCounter.increment()

        val tilganger = cache.getAsync(fnr) { _ ->
            altinnTilgangerClient.hentTilganger(selvbetjeningsToken)
        }

        if (tilganger.harFeil) {
            cache.synchronous().invalidate(fnr)
            failCounter.increment()
        } else {
            successCounter.increment()
        }

        tilganger
    }
}

