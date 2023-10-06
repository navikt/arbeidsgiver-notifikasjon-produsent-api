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

    suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> state.withLockApply {
                setOppgavetilstand(hendelse.notifikasjonId, NY)
                if (hendelse.påminnelse == null) {
                    return@withLockApply
                }
                add(
                    SkedulertPåminnelse(
                        oppgaveId = hendelse.notifikasjonId,
                        fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                        frist = hendelse.frist,
                        tidspunkt = hendelse.påminnelse.tidspunkt,
                        eksterneVarsler = hendelse.påminnelse.eksterneVarsler,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentId = hendelse.produsentId,
                        bestillingHendelseId = hendelse.hendelseId,
                    )
                )
            }
            
            is HendelseModel.FristUtsatt -> state.withLockApply {
                kansellerAllePåminnelserForOppgave(oppgaveId = hendelse.notifikasjonId)

                when (oppgavetilstand[hendelse.notifikasjonId]) {
                    NY -> {
                        registrerPåminnelse(hendelse)
                    }
                    UTGÅTT -> {
                        oppgavetilstand[hendelse.notifikasjonId] = NY
                        registrerPåminnelse(hendelse)
                    }
                    UTFØRT,
                    null -> {
                        /* noop */
                    }
                }
            }

            is HendelseModel.PåminnelseOpprettet ->
                kansellerBestilltPåminnelse(bestillingId = hendelse.bestillingHendelseId)

            is HendelseModel.OppgaveUtført -> state.withLockApply {
                oppgaveIdIndex[hendelse.notifikasjonId]?.let {
                    remove(it)
                }
                oppgavetilstand[hendelse.notifikasjonId] = UTFØRT
            }

            is HendelseModel.OppgaveUtgått -> state.withLockApply {
                oppgavetilstand[hendelse.notifikasjonId] = UTGÅTT
                kansellerAllePåminnelserForOppgave(hendelse.notifikasjonId)
            }

            is HendelseModel.SoftDelete,
            is HendelseModel.HardDelete -> state.withLockApply {
                kansellerAllePåminnelserForOppgave(hendelse.aggregateId)
                oppgavetilstand.remove(hendelse.aggregateId)
                Unit
            }

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    private fun State.registrerPåminnelse(hendelse: HendelseModel.FristUtsatt) {
        if (hendelse.påminnelse != null) {
            add(
                SkedulertPåminnelse(
                    oppgaveId = hendelse.notifikasjonId,
                    fristOpprettetTidspunkt = hendelse.fristEndretTidspunkt,
                    frist = hendelse.frist,
                    tidspunkt = hendelse.påminnelse.tidspunkt,
                    eksterneVarsler = hendelse.påminnelse.eksterneVarsler,
                    virksomhetsnummer = hendelse.virksomhetsnummer,
                    produsentId = hendelse.produsentId,
                    bestillingHendelseId = hendelse.hendelseId,
                )
            )
        }
    }

    private fun State.setOppgavetilstand(oppgaveId: UUID, tilstand: Oppgavetilstand) {
        oppgavetilstand[oppgaveId] = tilstand
    }

    private fun State.kansellerAllePåminnelserForOppgave(oppgaveId: OppgaveId) {
        oppgaveIdIndex[oppgaveId]?.let {
            remove(it)
        }
    }

    private fun State.oppgavenKanUtsettes(oppgaveId: UUID): Boolean {
        return oppgavetilstand[oppgaveId] == NY || oppgavetilstand[oppgaveId] == UTGÅTT
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


    private fun State.add(t: SkedulertPåminnelse) {
        bestillingsIdIndex[t.bestillingHendelseId] = t
        oppgaveIdIndex.computeIfAbsent(t.oppgaveId) { mutableListOf() }.add(t)
        påminnelseQueue.computeIfAbsent(t.queueKey) { mutableListOf() }.add(t)
    }


    private suspend fun kansellerBestilltPåminnelse(bestillingId: BestillingHendelseId) = state.withLockApply {
        bestillingsIdIndex[bestillingId]?.let {
            remove(it)
        }
    }

    private fun State.remove(skjedulertePåminnelser: Iterable<SkedulertPåminnelse>) {
        /* Duplicate list, because underlying list is modified by call to `remove`. */
        for (skjedulertPåminnelse in skjedulertePåminnelser.toList()) {
            remove(skjedulertPåminnelse)
        }
    }

    private fun State.remove(skedulertPåminelse: SkedulertPåminnelse) {
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

