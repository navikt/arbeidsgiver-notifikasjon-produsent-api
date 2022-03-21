package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.LocalDateTime
import java.util.*

interface Enhetsregisteret {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Underenhet(
        val organisasjonsnummer: String,
        val navn: String,
    )

    suspend fun hentUnderenhet(orgnr: String): Underenhet
}

fun enhetsregisterFactory() =
    basedOnEnv(
        prod = { EnhetsregisteretImpl() },
        other = { EnhetsregisteretDevImpl() }
    )

class EnhetsregisteretDevImpl: Enhetsregisteret {
    override suspend fun hentUnderenhet(orgnr: String) =
        Enhetsregisteret.Underenhet(
            organisasjonsnummer = orgnr,
            navn = ""
        )
}

class EnhetsregisteretImpl(
    private val baseUrl : String = "https://data.brreg.no"
) : Enhetsregisteret {
    private val log = logger()

    private val timer = Health.meterRegistry.timer("brreg_hent_organisasjon")

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        install(PropagateFromMDCFeature) {
            propagate("x_correlation_id")
        }
        expectSuccess = false
    }

    val cache = SimpleLRUCache<String, Enhetsregisteret.Underenhet>(100_000) { orgnr ->
        val response: HttpResponse = try {
            httpClient.get("$baseUrl/enhetsregisteret/api/underenheter/$orgnr")
        } catch (e: Exception) {
            log.warn("kall mot $baseUrl feilet", e)
            return@SimpleLRUCache Enhetsregisteret.Underenhet(orgnr, "")
        }
        if (response.status.isSuccess()) {
            try {
                response.receive()
            } catch (e: Exception) {
                log.warn("feil ved deserializing av response fra enhetsregisteret", e)
                Enhetsregisteret.Underenhet(orgnr, "")
            }
        } else {
            log.warn("kunne ikke finne navn for virksomhet. kall til brreg feilet: ${response.status} ${response.readText()}")
            Enhetsregisteret.Underenhet(orgnr, "")
        }
    }

    override suspend fun hentUnderenhet(orgnr: String): Enhetsregisteret.Underenhet =
        timer.coRecord {
            cache.get(orgnr)
        }
}

class SimpleLRUCache<K, V>(val maxCapacity : Int, val loader: suspend (K) -> V) {
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<K, ValueWithExpiry<V>>(maxCapacity, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, ValueWithExpiry<V>>): Boolean {
                return size > maxCapacity
            }

            override fun get(key: K): ValueWithExpiry<V>? {
                val value = super.get(key)
                return when {
                    value == null -> null
                    value.expired -> null
                    else -> value
                }
            }
        }
    )

    suspend fun get(key: K, load: suspend (K) -> V) : V {
        return cache.getOrPut(key) {
            ValueWithExpiry(load(key))
        }.value
    }

    suspend fun get(key: K) : V {
        return get(key) { loader(key) }
    }

    fun clear() {
        cache.clear()
    }
}

data class ValueWithExpiry<T> (
    val value : T,
    val expires : LocalDateTime = LocalDateTime.now().plusHours(12),
) {
    val expired: Boolean
        get() {
            return LocalDateTime.now().isAfter(expires)
        }
}
