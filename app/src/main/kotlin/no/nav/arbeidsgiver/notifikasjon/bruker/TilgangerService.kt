package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.erDriftsforstyrrelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.MottakerRegister

interface TilgangerService {
    suspend fun hentTilganger(
        altinn: Altinn,
        context: BrukerAPI.Context,
        altinnRolleService: AltinnRolleService
    ): List<BrukerModel.Tilgang>?
}

class TilgangerServiceImpl: TilgangerService {
    private val log = logger()

    override suspend fun hentTilganger(
        altinn: Altinn,
        context: BrukerAPI.Context,
        altinnRolleService: AltinnRolleService
    ): List<BrukerModel.Tilgang>? {
        return try {
            altinn.hentTilganger(
                context.fnr,
                context.token,
                MottakerRegister.servicecodeDefinisjoner,
                altinnRolleService.hentRoller(MottakerRegister.rolleDefinisjoner),
            )
        } catch (e: AltinnrettigheterProxyKlientFallbackException) {
            if (e.erDriftsforstyrrelse())
                log.info("Henting av Altinn-tilganger feilet", e)
            else
                log.error("Henting av Altinn-tilganger feilet", e)
            null
        } catch (e: Exception) {
            log.error("Henting av Altinn-tilganger feilet", e)
            null
        }
    }
}