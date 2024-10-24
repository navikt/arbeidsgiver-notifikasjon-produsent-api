package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.time.delay
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionProcessor
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTidImpl
import no.nav.arbeidsgiver.notifikasjon.tid.asOsloLocalDate
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*


class SkedulertUtgåttService(
    private val hendelseProdusent: HendelseProdusent,
    private val osloTid: OsloTid = OsloTidImpl
): PartitionProcessor {
    private val repository = SkedulertUtgåttRepository()

    override fun close() = repository.close()

    override suspend fun processHendelse(hendelse: HendelseModel.Hendelse, metadata: PartitionHendelseMetadata) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> {
                /* Vi må huske saks-id uavhengig av om det er frist på oppgaven, for
                 * det kan komme en frist senere. */
                if (hendelse.sakId != null) {
                    repository.huskSakOppgaveKobling(
                        sakId = hendelse.sakId,
                        oppgaveId = hendelse.aggregateId
                    )
                }

                if (hendelse.frist != null) {
                    repository.skedulerUtgått(
                        SkedulertUtgåttRepository.SkedulertUtgått(
                            oppgaveId = hendelse.notifikasjonId,
                            frist = hendelse.frist,
                            virksomhetsnummer = hendelse.virksomhetsnummer,
                            produsentId = hendelse.produsentId,
                        )
                    )
                }

                Unit
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
                    oppgaveId = hendelse.aggregateId,
                    utgaattTidspunkt = hendelse.utgaattTidspunkt.asOsloLocalDate()
                )

            is HendelseModel.OppgaveUtført ->
                repository.slettOppgave(aggregateId = hendelse.aggregateId)

            is HendelseModel.HardDelete -> {
                repository.slettOgHuskSlett(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.SoftDelete -> {
                repository.slettOgHuskSlett(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.KalenderavtaleOpprettet,
            is HendelseModel.KalenderavtaleOppdatert,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.NesteStegSak,
            is HendelseModel.TilleggsinformasjonSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselKansellert,
            is HendelseModel.EksterntVarselVellykket -> Unit

            is HendelseModel.OppgavePaaminnelseEndret -> TODO()
        }
    }

    override suspend fun processingLoopStep() {
        sendVedUtgåttFrist()
        delay(Duration.ofSeconds(1))
    }

    suspend fun sendVedUtgåttFrist(now: LocalDate = osloTid.localDateNow()) {
        val utgåttFrist = repository.hentOgFjernAlleMedFrist(now)
        utgåttFrist.forEach { utgått ->
            val fristLocalDateTime = LocalDateTime.of(utgått.frist, LocalTime.MAX)
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