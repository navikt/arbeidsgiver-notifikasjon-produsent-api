package no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun <T> blockingIO(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.IO, block)