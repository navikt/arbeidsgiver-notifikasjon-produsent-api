package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.MutexProtectedValue
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.Oppgavetilstand.*
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

private typealias OppgaveId = UUID
private typealias BestillingHendelseId = UUID

enum class Oppgavetilstand {
    UTFØRT, NY, UTGÅTT
}

class SkedulertPåminnelseRepository {
    private class State {
        val oppgaveIdIndex = HashMap<OppgaveId, MutableList<SkedulertPåminnelse>>()
        val bestillingsIdIndex = HashMap<BestillingHendelseId, SkedulertPåminnelse>()
        val påminnelseQueue = TreeMap<Instant, MutableList<SkedulertPåminnelse>>()
        val oppgavetilstand = HashMap<OppgaveId, Oppgavetilstand>()
    }

    private val state = MutexProtectedValue { State() }

    data class SkedulertPåminnelse(
        val oppgaveId: OppgaveId,
        val fristOpprettetTidspunkt: Instant,
        val frist: LocalDate?,
        val tidspunkt: HendelseModel.PåminnelseTidspunkt,
        val eksterneVarsler: List<HendelseModel.EksterntVarsel>,
        val virksomhetsnummer: String,
        val produsentId: String,
        val bestillingHendelseId: BestillingHendelseId,
    ) {
        val queueKey: Instant = tidspunkt.påminnelseTidspunkt.truncatedTo(ChronoUnit.HOURS)
    }

    suspend fun setOppgavetilstand(oppgaveId: UUID, tilstand: Oppgavetilstand) {
        state.withLockApply {
            oppgavetilstand[oppgaveId] = tilstand
        }
    }

    suspend fun setNyHvisUtgått(oppgaveId: OppgaveId) {
        state.withLockApply {
            if (oppgavetilstand[oppgaveId] == UTGÅTT) {
                oppgavetilstand[oppgaveId] = NY
            }
        }
    }

    suspend fun oppgaveErUtført(oppgaveId: UUID): Boolean =
        state.withLockApply {
            oppgavetilstand[oppgaveId] == UTFØRT
        }

    suspend fun hentOgFjernAlleAktuellePåminnelser(now: Instant): Collection<SkedulertPåminnelse> =
        state.withLockApply {
            val alleAktuelle = mutableListOf<SkedulertPåminnelse>()
            val harPassert = { it: SkedulertPåminnelse -> it.tidspunkt.påminnelseTidspunkt < now }

            while (
                påminnelseQueue.isNotEmpty()
                && påminnelseQueue.firstKey() < now
                && påminnelseQueue.firstEntry().value.any(harPassert)
            ) {
                val (_, potensiellePåminnelser) = påminnelseQueue.firstEntry()
                val aktuellePåminnelser = potensiellePåminnelser.filter(harPassert)
                alleAktuelle.addAll(aktuellePåminnelser)
                remove(aktuellePåminnelser)
            }
            return@withLockApply alleAktuelle
        }


    suspend fun add(t: SkedulertPåminnelse): Unit = state.withLockApply {
        bestillingsIdIndex[t.bestillingHendelseId] = t
        oppgaveIdIndex.computeIfAbsent(t.oppgaveId) { mutableListOf() }.add(t)
        påminnelseQueue.computeIfAbsent(t.queueKey) { mutableListOf() }.add(t)
    }

    suspend fun removeOppgavetilstand(oppgaveId: OppgaveId) {
        state.withLockApply {
            oppgavetilstand.remove(oppgaveId)
        }
    }
    suspend fun removeOppgaveId(oppgaveId: OppgaveId) {
        state.withLockApply {
            oppgaveIdIndex[oppgaveId]?.let {
                remove(it)
            }
        }
    }

    suspend fun removeBestillingId(bestillingId: BestillingHendelseId) = state.withLockApply {
        bestillingsIdIndex[bestillingId]?.let {
            remove(it)
        }
    }

    private fun State.remove(skjedulertePåminnelser: Iterable<SkedulertPåminnelseRepository.SkedulertPåminnelse>) {
        /* Duplicate list, because underlying list is modified by call to `remove`. */
        for (skjedulertPåminnelse in skjedulertePåminnelser.toList()) {
            remove(skjedulertPåminnelse)
        }
    }

    private fun State.remove(skedulertPåminelse: SkedulertPåminnelseRepository.SkedulertPåminnelse) {
        bestillingsIdIndex.remove(skedulertPåminelse.bestillingHendelseId)

        val oppgaveBestillinger = oppgaveIdIndex[skedulertPåminelse.oppgaveId]
        if (oppgaveBestillinger != null) {
            oppgaveBestillinger.removeIf { it.bestillingHendelseId == skedulertPåminelse.bestillingHendelseId }
            if (oppgaveBestillinger.isEmpty()) {
                oppgaveIdIndex.remove(skedulertPåminelse.oppgaveId)
            }
        }

        val alleMedSammePåminnelseTidspunkt = påminnelseQueue[skedulertPåminelse.queueKey] ?: return
        alleMedSammePåminnelseTidspunkt.removeIf { it.bestillingHendelseId == skedulertPåminelse.bestillingHendelseId }
        if (alleMedSammePåminnelseTidspunkt.isEmpty()) {
            påminnelseQueue.remove(skedulertPåminelse.queueKey)
        }
    }
}

