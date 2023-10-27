package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionProcessor
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*


class SkedulertUtgåttService(
    private val hendelseProdusent: HendelseProdusent
): PartitionProcessor {
    private val repository = SkedulertUtgåttRepository()

    override fun close() = repository.close()

    override fun processHendelse(hendelse: HendelseModel.Hendelse) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> {
                if (hendelse.frist == null) {
                    return
                }
                repository.skedulerUtgått(
                    SkedulertUtgåttRepository.SkedulertUtgått(
                        oppgaveId = hendelse.notifikasjonId,
                        frist = hendelse.frist,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentId = hendelse.produsentId,
                    )
                )
            }

            is HendelseModel.FristUtsatt -> {
                repository.skedulerUtgått(
                    SkedulertUtgåttRepository.SkedulertUtgått(
                        oppgaveId = hendelse.notifikasjonId,
                        frist = hendelse.frist,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentId = hendelse.produsentId,
                    )
                )
            }

            is HendelseModel.OppgaveUtgått ->
                repository.slettOmEldre(
                    hendelse.aggregateId,
                    hendelse.utgaattTidspunkt.asOsloLocalDate()
                )

            is HendelseModel.OppgaveUtført ->
                repository.slett(hendelse.aggregateId)

            is HendelseModel.HardDelete -> {
                if (hendelse.grupperingsid != null && hendelse.merkelapp != null) {
                    repository.huskSlettetSak(
                        grupperingsid = hendelse.grupperingsid,
                        merkelapp = hendelse.merkelapp,
                        sakId = hendelse.aggregateId,
                    )
                }
                repository.huskSlettetOppgave(hendelse.aggregateId)
                repository.slett(hendelse.aggregateId)
            }

            is HendelseModel.SoftDelete -> {
                repository.huskSlettetOppgave(hendelse.aggregateId)
                repository.slett(hendelse.aggregateId)
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

    override fun processingLoopStep() {
        sendVedUtgåttFrist(OsloTid.localDateNow())
        Thread.sleep(Duration.ofSeconds(1))
    }

    fun sendVedUtgåttFrist(now: LocalDate) {
        val utgåttFrist = repository.hentOgFjernAlleMedFrist(now)
        utgåttFrist.forEach { utgått ->
            val fristLocalDateTime = LocalDateTime.of(utgått.frist, LocalTime.MAX)

            runBlocking(Dispatchers.IO) {
                hendelseProdusent.send(HendelseModel.OppgaveUtgått(
                    virksomhetsnummer = utgått.virksomhetsnummer,
                    notifikasjonId = utgått.oppgaveId,
                    hendelseId = UUID.randomUUID(),
                    produsentId = utgått.produsentId,
                    kildeAppNavn = NaisEnvironment.clientId,
                    hardDelete = null,
                    utgaattTidspunkt = fristLocalDateTime.atOslo().toOffsetDateTime(),
                    nyLenke = null,
                ))
            }
        }
    }
}