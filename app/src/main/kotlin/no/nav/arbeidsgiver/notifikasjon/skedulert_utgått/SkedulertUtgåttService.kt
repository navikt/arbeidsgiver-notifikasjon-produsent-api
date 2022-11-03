package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class SkedulertUtgått(
    val oppgaveId: UUID,
    val frist: LocalDate,
    val virksomhetsnummer: String,
    val produsentId: String,
)

class IndexedPriorityQueue {
    private val mutex = Mutex()
    private val indexedLookup = HashMap<UUID, SkedulertUtgått>()
    private val fristQueue = TreeMap<LocalDate, MutableList<SkedulertUtgått>>()

    suspend fun hentOgFjernAlleMedFrist(localDateNow: LocalDate): Collection<SkedulertUtgått> =
        mutex.withLock {
            val alleUtgåtte = mutableListOf<SkedulertUtgått>()

            while (fristQueue.firstKey() < localDateNow) {
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

class SkedulertUtgåttService(
    private val hendelseProdusent: HendelseProdusent
) {
    private val skedulerteUtgått = IndexedPriorityQueue()

    suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            /* må håndtere */
            is HendelseModel.OppgaveOpprettet -> run {
                if (hendelse.frist == null) {
                    return@run
                }
                skedulerteUtgått.add(
                    SkedulertUtgått(
                        oppgaveId = hendelse.notifikasjonId,
                        frist = hendelse.frist,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentId = hendelse.produsentId,
                    )
                )
            }
            is HendelseModel.OppgaveUtført,
            is HendelseModel.OppgaveUtgått,
            is HendelseModel.HardDelete ->
                skedulerteUtgått.remove(hendelse.aggregateId)

            is HendelseModel.SoftDelete,
            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    suspend fun sendVedUtgåttFrist() {
        val utgåttFrist = skedulerteUtgått.hentOgFjernAlleMedFrist(OsloTid.localDateNow())
        /* TODO: rom for batching av utsendelse. */
        utgåttFrist.forEach { utgått ->
            hendelseProdusent.send(HendelseModel.OppgaveUtgått(
                virksomhetsnummer = utgått.virksomhetsnummer,
                notifikasjonId = utgått.oppgaveId,
                hendelseId = UUID.randomUUID(),
                produsentId = utgått.produsentId,
                kildeAppNavn = NaisEnvironment.clientId,
                hardDelete = null,
                utgaattTidspunkt = OffsetDateTime.now(),
            ))
        }
    }
}