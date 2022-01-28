package no.nav.arbeidsgiver.notifikasjon.altinn_roller

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository

interface AltinnRolleService {
    suspend fun lastAltinnroller()
}

class AltinnRolleServiceImpl(val altinn: Altinn, val produsentRepository: ProdusentRepository) : AltinnRolleService {
    override suspend fun lastAltinnroller() {
        val ferskeRollerFraAltinn = altinn.hentRoller()
        val eksisterendeRollerFraDb = produsentRepository.hentAlleAltinnRoller()
        val snittFerskeOgEksisterendeRoller = ferskeRollerFraAltinn - eksisterendeRollerFraDb
        snittFerskeOgEksisterendeRoller.forEach { rolle ->
            produsentRepository.leggTilAltinnRolle(rolle)
        }
    }

}