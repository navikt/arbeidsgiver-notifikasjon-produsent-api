package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class SkedulertHardDeleteService(
    private val repo: SkedulertHardDeleteRepository,
    private val hendelseProdusent: HendelseProdusent,
) {
    private val log = logger()

    suspend fun slettDeSomSkalSlettes(tilOgMed: Instant) {
        val skalSlettes = repo.hentDeSomSkalSlettes(tilOgMed = tilOgMed)

        skalSlettes.forEach {
            if (it.beregnetSlettetidspunkt > tilOgMed) {
                log.error("Beregnet slettetidspunkt kan ikke være i fremtiden. {}", it.loggableToString())
                Health.subsystemAlive[Subsystem.AUTOSLETT_SERVICE] = false
                return
            }

            hendelseProdusent.send(
                HendelseModel.HardDelete(
                    hendelseId = UUID.randomUUID(),
                    aggregateId = it.aggregateId,
                    virksomhetsnummer = it.virksomhetsnummer,
                    deletedAt = OffsetDateTime.now(),
                    produsentId = it.produsentid,
                    kildeAppNavn = NaisEnvironment.clientId,
                )
            )
        }
    }
}
