package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.*


class SkedulertUtgåttService(
    private val hendelseProdusent: HendelseProdusent
) {
    private val skedulerteUtgått = SkedulertUtgåttRepository()

    suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            /* må håndtere */
            is HendelseModel.OppgaveOpprettet -> run {
                if (hendelse.frist == null) {
                    return@run
                }
                skedulerteUtgått.add(
                    SkedulertUtgåttRepository.SkedulertUtgått(
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
        /* NB! Her kan vi vurdere å innføre batching av utsendelse. */
        utgåttFrist.forEach { utgått ->
            val fristLocalDateTime = LocalDateTime.of(utgått.frist, LocalTime.MAX)
            hendelseProdusent.send(HendelseModel.OppgaveUtgått(
                virksomhetsnummer = utgått.virksomhetsnummer,
                notifikasjonId = utgått.oppgaveId,
                hendelseId = UUID.randomUUID(),
                produsentId = utgått.produsentId,
                kildeAppNavn = NaisEnvironment.clientId,
                hardDelete = null,
                utgaattTidspunkt = fristLocalDateTime.atOslo().toOffsetDateTime()
            ))
        }
    }
}