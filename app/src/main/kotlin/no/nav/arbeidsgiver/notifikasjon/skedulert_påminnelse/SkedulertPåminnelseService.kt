package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
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
            /* må håndtere */
            is HendelseModel.OppgaveOpprettet -> run {
                if (hendelse.påminnelse == null) {
                    return@run
                }
                repository.add(
                    SkedulertPåminnelseRepository.SkedulertPåminnelse(
                        oppgaveId = hendelse.notifikasjonId,
                        oppgaveOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                        frist = hendelse.frist,
                        tidspunkt = hendelse.påminnelse.tidspunkt,
                        eksterneVarsler = hendelse.påminnelse.eksterneVarsler,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentId = hendelse.produsentId,
                    )
                )
            }
            is HendelseModel.OppgaveUtført,
            is HendelseModel.OppgaveUtgått,
            is HendelseModel.SoftDelete,
            is HendelseModel.HardDelete ->
                repository.remove(hendelse.aggregateId)

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    suspend fun sendAktuellePåminnelser(now: Instant = OsloTid.localDateTimeNow().inOsloAsInstant()) {
        val skedulertePåminnelser = repository.hentOgFjernAlleAktuellePåminnelser(now)
        /* NB! Her kan vi vurdere å innføre batching av utsendelse. */
        skedulertePåminnelser.forEach { skedulert ->
            // TODO: send PåminnelseOpprettet event
            hendelseProdusent.send(HendelseModel.PåminnelseOpprettet(
                virksomhetsnummer = skedulert.virksomhetsnummer,
                notifikasjonId = skedulert.oppgaveId,
                hendelseId = UUID.randomUUID(),
                produsentId = skedulert.produsentId,
                kildeAppNavn = NaisEnvironment.clientId,
                opprettetTidpunkt = Instant.now(),
                oppgaveOpprettetTidspunkt = skedulert.oppgaveOpprettetTidspunkt,
                frist = skedulert.frist,
                tidspunkt = skedulert.tidspunkt,
                eksterneVarsler = skedulert.eksterneVarsler,
            ))
        }
    }
}