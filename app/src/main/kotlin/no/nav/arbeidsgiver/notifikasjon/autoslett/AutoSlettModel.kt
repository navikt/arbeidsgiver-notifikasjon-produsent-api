package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class AutoSlettModel(
    private val autoSlettRepository: AutoSlettRepository,
) {
    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, timestamp: Instant) {
        val ignored = when (hendelse) {
            is HendelseModel.BeskjedOpprettet -> {
                autoSlettRepository.saveAggregate(hendelse, "Beskjed", hendelse.merkelapp)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                )
            }

            is HendelseModel.OppgaveOpprettet -> {
                autoSlettRepository.saveAggregate(hendelse, "Oppgave", hendelse.merkelapp)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                )
            }

            is HendelseModel.SakOpprettet -> {
                autoSlettRepository.saveAggregate(hendelse, "Sak", hendelse.merkelapp)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                )
            }

            is HendelseModel.OppgaveUtført -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = timestamp.atOffset(ZoneOffset.UTC),
                    eksisterende = autoSlettRepository.hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.NyStatusSak -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                    hardDelete = hendelse.hardDelete,
                    eksisterende = autoSlettRepository.hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.HardDelete -> autoSlettRepository.hardDelete(hendelse.aggregateId)
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SoftDelete -> Unit
        }
    }


    private suspend fun upsert(
        aggregateId: UUID,
        hardDelete: HendelseModel.HardDeleteUpdate?,
        opprettetTidspunkt: OffsetDateTime,
        eksisterende: SkedulertHardDelete?,
    ) {
        if (hardDelete == null) return
        val scheduledTime = ScheduledTime(hardDelete.nyTid, opprettetTidspunkt)
        if (
            eksisterende != null &&
            hardDelete.strategi == HendelseModel.NyTidStrategi.FORLENG &&
            scheduledTime.happensAt().isBefore(eksisterende.beregnetSlettetidspunkt)
        ) {
            return
        }
        upsert(aggregateId, hardDelete.nyTid, opprettetTidspunkt)
    }

    private suspend fun upsert(
        aggregateId: UUID,
        hardDelete: HendelseModel.LocalDateTimeOrDuration?,
        opprettetTidspunkt: OffsetDateTime,
    ) {
        if (hardDelete == null) return
        val scheduledTime = ScheduledTime(hardDelete, opprettetTidspunkt)
        autoSlettRepository.upsert(aggregateId, scheduledTime)
    }

}