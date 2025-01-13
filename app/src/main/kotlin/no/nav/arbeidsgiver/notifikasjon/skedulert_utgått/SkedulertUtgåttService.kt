package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTidImpl
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*


class SkedulertUtgåttService(
    private val repository: SkedulertUtgåttRepository,
    private val hendelseProdusent: HendelseProdusent,
    private val osloTid: OsloTid = OsloTidImpl
) {
    suspend fun settOppgaverUtgåttBasertPåFrist(now: LocalDate = osloTid.localDateNow()) {
        val utgåttFrist = repository.hentOgFjernAlleUtgåtteOppgaver(now)
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

    suspend fun settKalenderavtalerAvholdtBasertPåTidspunkt(now: LocalDateTime = osloTid.localDateTimeNow()) {
        repository.hentOgFjernAlleAvholdteKalenderavtaler(now).forEach { avholdt ->
            hendelseProdusent.send(HendelseModel.KalenderavtaleOppdatert(
                virksomhetsnummer = avholdt.virksomhetsnummer,
                notifikasjonId = avholdt.kalenderavtaleId,
                hendelseId = UUID.randomUUID(),
                produsentId = avholdt.produsentId,
                kildeAppNavn = NaisEnvironment.clientId,
                merkelapp = avholdt.merkelapp,
                grupperingsid = avholdt.grupperingsid,
                opprettetTidspunkt = avholdt.opprettetTidspunkt,
                oppdatertTidspunkt = Instant.now(),
                tilstand = HendelseModel.KalenderavtaleTilstand.AVHOLDT,
                lenke = null,
                tekst = null,
                startTidspunkt = null,
                sluttTidspunkt = null,
                lokasjon = null,
                erDigitalt = null,
                påminnelse = null,
                idempotenceKey = null,
                hardDelete = null,
                eksterneVarsler = emptyList(),
            ))
        }
    }
}