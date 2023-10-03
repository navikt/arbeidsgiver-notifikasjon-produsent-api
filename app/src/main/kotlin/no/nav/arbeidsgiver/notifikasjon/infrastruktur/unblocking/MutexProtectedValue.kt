package no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking

import kotlinx.coroutines.sync.withLock

class MutexProtectedValue<T>(init: () -> T) {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val value = init()

    suspend fun <ReturnType>withLockApply(body: T.() -> ReturnType): ReturnType = mutex.withLock {
        value.body()
    }
}