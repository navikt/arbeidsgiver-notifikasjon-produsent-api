package no.nav.arbeidsgiver.notifikasjon.altinn_roller

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.toList
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.AltinnRolleDefinisjon

interface AltinnRolleService {
    suspend fun lastAltinnroller()
    suspend fun hentAltinnrollerMedRetry(rolleDefinisjoner: List<AltinnRolleDefinisjon>, retries: Long, delay: Long) : List<AltinnRolle>
}

class AltinnRolleServiceImpl(val altinn: Altinn, val altinnRolleRepository: AltinnRolleRepository) : AltinnRolleService {
    override suspend fun lastAltinnroller() {
        val ferskeRollerFraAltinn = altinn.hentRoller()
        val eksisterendeRollerFraDb = altinnRolleRepository.hentAlleAltinnRoller()
        val nyeRoller = ferskeRollerFraAltinn - eksisterendeRollerFraDb.toSet()
        altinnRolleRepository.leggTilAltinnRoller(nyeRoller)
    }

    override suspend fun hentAltinnrollerMedRetry(
        rolleDefinisjoner: List<AltinnRolleDefinisjon>,
        retries: Long,
        delay: Long
    ) : List<AltinnRolle> {
        return rolleDefinisjoner.asFlow().map {
            altinnRolleRepository.hentAltinnrolle(it.roleCode)!!
        }.retry(retries) {
            Bruker.log.warn("feil ved henting av altinn rolle fra db. forsøker på nytt", it)
            delay(delay)
            true
        }.toList()
    }

}