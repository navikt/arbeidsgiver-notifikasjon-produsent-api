package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.*

class SimpleLRUCache<K: Any, V>(maxCapacity : Int, val loader: suspend (K) -> V) {
    private val cache = ExpiringMap<K, V>(maxCapacity)
    private val mutexes = MutexMap<K>()

    suspend fun get(key: K) : V =
        mutexes.withLock(key) {
            cache[key] ?: loader(key).also {
                cache[key] = it
            }
        }

    fun put(key: K, value: V) {
        cache[key] = value
    }
}

private class MutexMap<K> {
    private val mutexes: MutableMap<K, Mutex> = Collections.synchronizedMap(WeakHashMap())

    private fun getMutex(key: K): Mutex = mutexes.computeIfAbsent(key) { Mutex() }

    suspend fun <T> withLock(key: K, body: suspend () -> T): T =
        getMutex(key).withLock {
            body()
        }
}

private class ExpiringMap<K, V>(
    private val maxCapacity: Int
) {
    private val expiryMap = Collections.synchronizedMap(
        object : LinkedHashMap<K, ValueWithExpiry<V>>(16, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, ValueWithExpiry<V>>) =
                size > maxCapacity || eldest.value.expired
        }
    )

    private class ValueWithExpiry<T>(val value: T) {
        val expires: Instant = Instant.now() + Duration.ofHours(12)
        val expired: Boolean
            get() = Instant.now().isAfter(expires)
    }

    operator fun set(key: K, value: V) {
        expiryMap[key] = ValueWithExpiry(value)
    }

    operator fun get(key: K): V? {
        val value = expiryMap[key]
        return when {
            value == null -> null
            value.expired -> null
            else -> value.value
        }
    }
}
