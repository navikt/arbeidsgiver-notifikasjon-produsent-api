package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.AltinnRolleDefinisjon

interface AltinnRolleService {
    suspend fun lastRollerFraAltinn()
    suspend fun hentRoller(rolleDefinisjoner: Iterable<AltinnRolleDefinisjon>): Iterable<AltinnRolle>
}

class AltinnRolleServiceImpl(
    private val altinnRolleClient: AltinnRolleClient,
    private val altinnRolleRepository: AltinnRolleRepository,
) : AltinnRolleService {

    @Volatile
    private var alleRollerByCode: Map<String, AltinnRolle>? = null

    override suspend fun lastRollerFraAltinn() {
        val ferskeRollerFraAltinn = altinnRolleClient.hentRoller().orEmpty()
        val eksisterendeRollerFraDb = altinnRolleRepository.hentAlleAltinnRoller()
        val nyeRoller = ferskeRollerFraAltinn - eksisterendeRollerFraDb.toSet()
        altinnRolleRepository.leggTilAltinnRoller(nyeRoller)
        hentOgSettAlleRollerByCode()
    }

    override suspend fun hentRoller(rolleDefinisjoner: Iterable<AltinnRolleDefinisjon>): Iterable<AltinnRolle> {
        val roller = alleRollerByCode ?: hentOgSettAlleRollerByCode()
        return rolleDefinisjoner.map {
            roller[it.roleCode] ?: throw RuntimeException("fant ikke altinn rolle med kode=${it.roleCode}")
        }
    }

    private suspend fun hentOgSettAlleRollerByCode(): Map<String, AltinnRolle> =
        altinnRolleRepository
            .hentAlleAltinnRoller()
            .associateBy(AltinnRolle::RoleDefinitionCode)
            .also {
                alleRollerByCode = it
            }
}