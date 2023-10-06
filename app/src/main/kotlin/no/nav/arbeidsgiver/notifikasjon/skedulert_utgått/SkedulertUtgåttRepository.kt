package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.MutexProtectedValue
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class SkedulertUtgåttRepository {
    class SkedulertUtgått(
        val oppgaveId: UUID,
        val frist: LocalDate,
        val virksomhetsnummer: String,
        val produsentId: String,
    )

    private class State {
        val indexedLookup = HashMap<UUID, SkedulertUtgått>()
        val fristQueue = TreeMap<LocalDate, MutableList<SkedulertUtgått>>()
        val deleted = HashSet<UUID>()
    }

    private val state = MutexProtectedValue { State() }

    suspend fun hentOgFjernAlleMedFrist(localDateNow: LocalDate): Collection<SkedulertUtgått> =
        state.withLockApply {
            val alleUtgåtte = mutableListOf<SkedulertUtgått>()

            while (fristQueue.isNotEmpty() && fristQueue.firstKey() < localDateNow) {
                val utgåttFrist = fristQueue.firstKey()
                val utgåtteOppgaver = fristQueue.remove(utgåttFrist) ?: listOf()

                utgåtteOppgaver.forEach {
                    indexedLookup.remove(it.oppgaveId)
                }

                alleUtgåtte.addAll(utgåtteOppgaver)
            }

            return@withLockApply alleUtgåtte
        }

    private fun State.upsert(skedulertUtgått: SkedulertUtgått) {
        if (skedulertUtgått.oppgaveId in deleted) return

        remove(skedulertUtgått.oppgaveId)
        indexedLookup[skedulertUtgått.oppgaveId] = skedulertUtgått
        val alleMedSammeFrist = fristQueue.computeIfAbsent(skedulertUtgått.frist) { mutableListOf() }
        alleMedSammeFrist.add(skedulertUtgått)
    }

    private fun State.removeIfOlderThan(aggregateId: UUID, utgaattTidspunkt: OffsetDateTime)  {
        val skedulertUtgått = indexedLookup[aggregateId] ?: return

        if (skedulertUtgått.frist <= utgaattTidspunkt.asOsloLocalDate()) {
            remove(aggregateId)
        }
    }

    private fun State.remove(id: UUID) {
        val removed = indexedLookup.remove(id) ?: return
        val alleMedSammeFrist = fristQueue[removed.frist] ?: return
        alleMedSammeFrist.removeIf { it.oppgaveId == id }
        if (alleMedSammeFrist.isEmpty()) {
            fristQueue.remove(removed.frist)
        }
    }

    suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> run {
                if (hendelse.frist == null) {
                    return@run
                }
                state.withLockApply {
                    upsert(
                        SkedulertUtgått(
                            oppgaveId = hendelse.notifikasjonId,
                            frist = hendelse.frist,
                            virksomhetsnummer = hendelse.virksomhetsnummer,
                            produsentId = hendelse.produsentId,
                        )
                    )
                }
            }

            is HendelseModel.FristUtsatt -> {
                state.withLockApply {
                    upsert(
                        SkedulertUtgått(
                            oppgaveId = hendelse.notifikasjonId,
                            frist = hendelse.frist,
                            virksomhetsnummer = hendelse.virksomhetsnummer,
                            produsentId = hendelse.produsentId,
                        )
                    )
                }
            }

            is HendelseModel.OppgaveUtgått ->
                state.withLockApply {
                    removeIfOlderThan(hendelse.aggregateId, hendelse.utgaattTidspunkt)
                }

            is HendelseModel.OppgaveUtført ->
                state.withLockApply {
                    remove(hendelse.aggregateId)
                }
            is HendelseModel.HardDelete,
            is HendelseModel.SoftDelete ->
                state.withLockApply {
                    deleted.add(hendelse.aggregateId)
                    remove(hendelse.aggregateId)
                }

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }
}

