package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.supervisorScope
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger.Companion.flatten
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.coRecord
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon
import java.time.Duration

interface Altinn {
    suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>,
    ): Tilganger
}

class AltinnTilgangerImpl(
    private val altinnTilgangerClient: AltinnTilgangerClient
): Altinn {
    // TODO: midlertidig plugget inn i eksisterende interface, underliggende implementasjon bruker bare token
    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>
    ): Tilganger = altinnTilgangerClient.hentTilganger(selvbetjeningsToken)
}

class AltinnImpl(
    private val klient: SuspendingAltinnClient,
) : Altinn {
    private val log = logger()
    private val timer = Metrics.meterRegistry.timer("altinn_klient_hent_alle_tilganger")

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>,
    ): Tilganger =
        timer.coRecord {
            supervisorScope {
                val tjenesteTilganger = tjenester.map {
                    val (code, version) = it
                    async {
                        hentTilganger(fnr, code, version, selvbetjeningsToken)
                    }
                }
                return@supervisorScope tjenesteTilganger.awaitAll().flatten()
            }
        }

    private suspend fun hentTilganger(
        fnr: String,
        serviceCode: String,
        serviceEdition: String,
        selvbetjeningsToken: String,
    ): Tilganger {
        val reporteeList = klient.hentOrganisasjoner(
            SelvbetjeningToken(selvbetjeningsToken),
            Subject(fnr),
            ServiceCode(serviceCode),
            ServiceEdition(serviceEdition),
            false
        ) ?: return Tilganger.FAILURE

        return Tilganger(reporteeList
            .filter { it.type != "Enterprise" }
            .filterNot { it.type == "Person" && it.organizationNumber == null }
            .filter {
                if (it.organizationNumber == null) {
                    log.warn("filtrerer ut reportee uten organizationNumber: organizationForm=${it.organizationForm} type=${it.type} status=${it.status}")
                    false
                } else {
                    true
                }
            }
            .map {
                BrukerModel.Tilgang.Altinn(
                    virksomhet = it.organizationNumber!!,
                    servicecode = serviceCode,
                    serviceedition = serviceEdition
                )
            }
        )
    }
}

class AltinnCachedImpl(
    klient: SuspendingAltinnClient,
    maxCacheSize: Long = 10_000,
    cacheExpiry: Duration = Duration.ofMinutes(10)
) : Altinn {

    private val altinnImpl = AltinnImpl(klient)
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(cacheExpiry)
        .maximumSize(maxCacheSize)
        .buildAsync<TilgangerCacheKey, Tilganger>()

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>,
    ): Tilganger =
        cache.getAsync(TilgangerCacheKey(fnr, tjenester)) { cacheKey ->
            altinnImpl.hentTilganger(cacheKey.fnr, selvbetjeningsToken, cacheKey.tjenester)
        }
}

internal data class TilgangerCacheKey(
    val fnr: String,
    val tjenester: Iterable<ServicecodeDefinisjon>,
)

suspend fun <K : Any, V : Any> AsyncCache<K, V>.getAsync(key: K, loader: suspend (K) -> V): V =
    supervisorScope {
        get(key) { key, _ ->
            future {
                loader(key)
            }
        }.await()
    }