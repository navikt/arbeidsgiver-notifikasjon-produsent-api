package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.Oppgavetilstand.*
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import java.time.Instant
import java.util.*


class SkedulertPåminnelseService(
    private val hendelseProdusent: HendelseProdusent
) {
    private val repository = SkedulertPåminnelseRepository()

    suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> run {
                repository.setOppgavetilstand(hendelse.notifikasjonId, NY)
                if (hendelse.påminnelse == null) {
                    return@run
                }
                repository.add(
                    SkedulertPåminnelseRepository.SkedulertPåminnelse(
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
            is HendelseModel.FristUtsatt -> run {
                repository.setNyHvisUtgått(hendelse.notifikasjonId)
                if (hendelse.påminnelse == null) {
                    return@run
                }
                if (repository.oppgaveErUtført(hendelse.notifikasjonId)) {
                    return@run
                }
                repository.add(
                    SkedulertPåminnelseRepository.SkedulertPåminnelse(
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
            is HendelseModel.PåminnelseOpprettet ->
                repository.removeBestillingId(hendelse.bestillingHendelseId)

            is HendelseModel.OppgaveUtført -> {
                repository.removeOppgaveId(hendelse.notifikasjonId)
                repository.setOppgavetilstand(hendelse.notifikasjonId, UTFØRT)
            }
            is HendelseModel.OppgaveUtgått -> {
                repository.setOppgavetilstand(hendelse.notifikasjonId, UTGÅTT)
                repository.removeOppgaveId(hendelse.notifikasjonId)
            }
            is HendelseModel.SoftDelete,
            is HendelseModel.HardDelete -> {
                repository.removeOppgaveId(hendelse.aggregateId)
                repository.removeOppgavetilstand(hendelse.aggregateId)
            }
            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    suspend fun sendAktuellePåminnelser(now: Instant = OsloTid.localDateTimeNow().inOsloAsInstant()) {
        val skedulertePåminnelser = repository.hentOgFjernAlleAktuellePåminnelser(now)
        /* NB! Her kan vi vurdere å innføre batching av utsendelse. */
        skedulertePåminnelser.forEach { skedulert ->
            hendelseProdusent.send(HendelseModel.PåminnelseOpprettet(
                virksomhetsnummer = skedulert.virksomhetsnummer,
                notifikasjonId = skedulert.oppgaveId,
                hendelseId = UUID.randomUUID(),
                produsentId = skedulert.produsentId,
                kildeAppNavn = NaisEnvironment.clientId,
                opprettetTidpunkt = Instant.now(),
                fristOpprettetTidspunkt = skedulert.fristOpprettetTidspunkt,
                frist = skedulert.frist,
                tidspunkt = skedulert.tidspunkt,
                eksterneVarsler = skedulert.eksterneVarsler,
                bestillingHendelseId = skedulert.bestillingHendelseId,
            ))
        }
    }
}