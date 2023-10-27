package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionProcessor
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.Oppgavetilstand.*
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import java.time.Duration
import java.time.Instant
import java.util.*


class SkedulertPåminnelseService(
    private val hendelseProdusent: HendelseProdusent
) : PartitionProcessor {
    private val repository = SkedulertPåminnelseRepository()

    override fun processHendelse(hendelse: HendelseModel.Hendelse) {
        runBlocking(Dispatchers.IO) {
            repository.processHendelse(hendelse)
        }
    }

    override fun processingLoopStep() {
        runBlocking(Dispatchers.IO) {
            sendAktuellePåminnelser(now = OsloTid.localDateTimeNow().inOsloAsInstant())
        }
        Thread.sleep(Duration.ofSeconds(1))
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

    override fun close() {
    }
}