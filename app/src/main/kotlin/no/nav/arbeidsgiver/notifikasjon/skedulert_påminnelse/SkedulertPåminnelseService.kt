package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTidImpl
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import java.time.Instant
import java.util.*


class SkedulertPåminnelseService(
    private val hendelseProdusent: HendelseProdusent,
    private val osloTid: OsloTid = OsloTidImpl,
    database: Database,
) {
    private val repository = SkedulertPåminnelseRepository(database)

    suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        repository.processHendelse(hendelse)
    }

    suspend fun sendAktuellePåminnelser(now: Instant = osloTid.localDateTimeNow().inOsloAsInstant()) {
        val skedulertePåminnelser = repository.hentOgFjernAlleAktuellePåminnelser(now)
        /* NB! Her kan vi vurdere å innføre batching av utsendelse. */
        skedulertePåminnelser.forEach { skedulert ->
            hendelseProdusent.send(
                HendelseModel.PåminnelseOpprettet(
                    virksomhetsnummer = skedulert.virksomhetsnummer,
                    notifikasjonId = skedulert.notifikasjonId,
                    hendelseId = UUID.randomUUID(),
                    produsentId = skedulert.produsentId,
                    kildeAppNavn = NaisEnvironment.clientId,
                    opprettetTidpunkt = Instant.now(),
                    fristOpprettetTidspunkt = skedulert.hendelseOpprettetTidspunkt,
                    frist = skedulert.frist,
                    tidspunkt = skedulert.tidspunkt,
                    eksterneVarsler = skedulert.eksterneVarsler,
                    bestillingHendelseId = skedulert.bestillingHendelseId,
                )
            )
        }
    }

    fun close() {
        repository.close()
    }
}