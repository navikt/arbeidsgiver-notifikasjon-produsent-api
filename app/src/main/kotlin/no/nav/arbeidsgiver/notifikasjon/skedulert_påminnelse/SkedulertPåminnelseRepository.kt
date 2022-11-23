package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import java.time.Instant
import java.time.LocalDate
import java.util.*

class SkedulertPåminnelseRepository {
    private val mutex = Mutex()
    private val indexedLookup = HashMap<UUID, SkedulertPåminnelse>()
    private val påminnelseQueue = TreeMap<Instant, MutableList<SkedulertPåminnelse>>()

    class SkedulertPåminnelse(
        val oppgaveId: UUID,
        val oppgaveOpprettetTidspunkt: Instant,
        val frist: LocalDate?,
        val tidspunkt: HendelseModel.PåminnelseTidspunkt,
        val eksterneVarsler: List<HendelseModel.EksterntVarsel>,
        val virksomhetsnummer: String,
        val produsentId: String,
    )

    suspend fun hentOgFjernAlleAktuellePåminnelser(now: Instant): Collection<SkedulertPåminnelse> =
        mutex.withLock {
            val alleAktuelle = mutableListOf<SkedulertPåminnelse>()

            while (påminnelseQueue.isNotEmpty() && påminnelseQueue.firstKey() < now) {
                val aktuellPåminnelse = påminnelseQueue.firstKey()
                val aktuellePåminnelser = påminnelseQueue.remove(aktuellPåminnelse) ?: listOf()

                aktuellePåminnelser.forEach {
                    indexedLookup.remove(it.oppgaveId)
                }

                alleAktuelle.addAll(aktuellePåminnelser)
            }

            return@withLock alleAktuelle
        }

    suspend fun add(t: SkedulertPåminnelse): Unit = mutex.withLock {
        indexedLookup[t.oppgaveId] = t
        val alleMedSammeFrist = påminnelseQueue.computeIfAbsent(t.tidspunkt.påminnelseTidspunkt) { mutableListOf() }
        alleMedSammeFrist.add(t)
    }

    suspend fun remove(id: UUID) = mutex.withLock {
        val removed = indexedLookup.remove(id) ?: return@withLock
        val alleMedSammePåminnelseTidspunkt = påminnelseQueue[removed.tidspunkt.påminnelseTidspunkt] ?: return@withLock
        alleMedSammePåminnelseTidspunkt.removeIf { it.oppgaveId == id }
        if (alleMedSammePåminnelseTidspunkt.isEmpty()) {
            påminnelseQueue.remove(removed.tidspunkt.påminnelseTidspunkt)
        }
    }
}