package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.*

class SkedulertUtgåttRepository {
    private val mutex = Mutex()
    private val indexedLookup = HashMap<UUID, SkedulertUtgått>()
    private val fristQueue = TreeMap<LocalDate, MutableList<SkedulertUtgått>>()

    class SkedulertUtgått(
        val oppgaveId: UUID,
        val frist: LocalDate,
        val virksomhetsnummer: String,
        val produsentId: String,
    )

    suspend fun hentOgFjernAlleMedFrist(localDateNow: LocalDate): Collection<SkedulertUtgått> =
        mutex.withLock {
            val alleUtgåtte = mutableListOf<SkedulertUtgått>()

            while (fristQueue.isNotEmpty() && fristQueue.firstKey() < localDateNow) {
                val utgåttFrist = fristQueue.firstKey()
                val utgåtteOppgaver = fristQueue.remove(utgåttFrist) ?: listOf()

                utgåtteOppgaver.forEach {
                    indexedLookup.remove(it.oppgaveId)
                }

                alleUtgåtte.addAll(utgåtteOppgaver)
            }

            return@withLock alleUtgåtte
        }

    suspend fun add(t: SkedulertUtgått): Unit = mutex.withLock {
        indexedLookup[t.oppgaveId] = t
        val alleMedSammeFrist = fristQueue.computeIfAbsent(t.frist) { mutableListOf() }
        alleMedSammeFrist.add(t)
    }

    suspend fun remove(id: UUID) = mutex.withLock {
        val removed = indexedLookup.remove(id) ?: return@withLock
        val alleMedSammeFrist = fristQueue[removed.frist] ?: return@withLock
        alleMedSammeFrist.removeIf { it.oppgaveId == id }
        if (alleMedSammeFrist.isEmpty()) {
            fristQueue.remove(removed.frist)
        }
    }
}