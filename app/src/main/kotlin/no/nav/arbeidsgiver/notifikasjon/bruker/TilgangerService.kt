package no.nav.arbeidsgiver.notifikasjon.bruker

import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.MottakerRegister

interface TilgangerService {
    suspend fun hentTilganger(context: BrukerAPI.Context): Tilganger
}

class TilgangerServiceImpl(
    private val altinn: Altinn,
): TilgangerService {
    override suspend fun hentTilganger(context: BrukerAPI.Context) =
        altinn.hentTilganger(
            context.fnr,
            context.token,
            MottakerRegister.servicecodeDefinisjoner,
        )
}