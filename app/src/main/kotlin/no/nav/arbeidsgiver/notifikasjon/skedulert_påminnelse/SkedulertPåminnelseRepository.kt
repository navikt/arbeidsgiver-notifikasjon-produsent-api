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
                oppgavetilstand[hendelse.notifikasjonId] = NY

                bestillPåminnelse(
                    hendelse = hendelse,
                    påminnelse = hendelse.påminnelse,
                    fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    frist = hendelse.frist,
                )
            }

            is HendelseModel.FristUtsatt -> state.withLockApply {
                kansellerAllePåminnelserForOppgave(oppgaveId = hendelse.notifikasjonId)

                when (oppgavetilstand[hendelse.notifikasjonId]) {
                    NY -> {
                        bestillPåminnelse(
                            hendelse = hendelse,
                            påminnelse = hendelse.påminnelse,
                            frist = hendelse.frist,
                            fristOpprettetTidspunkt = hendelse.fristEndretTidspunkt,
                        )
                    }
                    UTGÅTT -> {
                        oppgavetilstand[hendelse.notifikasjonId] = NY
                        bestillPåminnelse(
                            hendelse = hendelse,
                            påminnelse = hendelse.påminnelse,
                            frist = hendelse.frist,
                            fristOpprettetTidspunkt = hendelse.fristEndretTidspunkt,
                        )
                    }
                    UTFØRT,
                    null -> {
                        /* noop */
                    }
                }
            }

            is HendelseModel.PåminnelseOpprettet -> state.withLockApply {
                kansellerBestilltPåminnelse(bestillingId = hendelse.bestillingHendelseId)
            }

            is HendelseModel.OppgaveUtført -> state.withLockApply {
                oppgavetilstand[hendelse.notifikasjonId] = UTFØRT
                kansellerAllePåminnelserForOppgave(hendelse.notifikasjonId)
            }

            is HendelseModel.OppgaveUtgått -> state.withLockApply {
                oppgavetilstand[hendelse.notifikasjonId] = UTGÅTT
                kansellerAllePåminnelserForOppgave(hendelse.notifikasjonId)
            }

            is HendelseModel.SoftDelete,
            is HendelseModel.HardDelete -> state.withLockApply {
                oppgavetilstand.remove(hendelse.aggregateId)
                kansellerAllePåminnelserForOppgave(hendelse.aggregateId)
            }

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    private fun State.bestillPåminnelse(
        hendelse: HendelseModel.Hendelse,
        påminnelse: HendelseModel.Påminnelse?,
        fristOpprettetTidspunkt: Instant,
        frist: LocalDate?,
    ) {
        if (påminnelse == null) {
            return
        }

        SkedulertPåminnelse(
            oppgaveId = hendelse.aggregateId,
            fristOpprettetTidspunkt = fristOpprettetTidspunkt,
            frist = frist,
            tidspunkt = påminnelse.tidspunkt,
            eksterneVarsler = påminnelse.eksterneVarsler,
            virksomhetsnummer = hendelse.virksomhetsnummer,
            produsentId = hendelse.produsentId!!,
            bestillingHendelseId = hendelse.hendelseId,
        ).let {
            bestillingsIdIndex[it.bestillingHendelseId] = it
            oppgaveIdIndex.computeIfAbsent(it.oppgaveId) { mutableListOf() }.add(it)
            påminnelseQueue.computeIfAbsent(it.queueKey) { mutableListOf() }.add(it)
        }
    }

    private fun State.kansellerAllePåminnelserForOppgave(oppgaveId: OppgaveId) {
        oppgaveIdIndex[oppgaveId]?.let {
            kansellerBestillinger(it)
        }
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
                kansellerBestillinger(aktuellePåminnelser)
            }
            return@withLockApply alleAktuelle
        }

    private fun State.kansellerBestilltPåminnelse(bestillingId: BestillingHendelseId) {
        bestillingsIdIndex[bestillingId]?.let {
            kansellerBestilling(it)
        }
    }

    private fun State.kansellerBestillinger(skjedulertePåminnelser: Iterable<SkedulertPåminnelse>) {
        /* Duplicate list, because underlying list is modified by call to `remove`. */
        for (skjedulertPåminnelse in skjedulertePåminnelser.toList()) {
            kansellerBestilling(skjedulertPåminnelse)
        }
    }

    private fun State.kansellerBestilling(skedulertPåminelse: SkedulertPåminnelse) {
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

