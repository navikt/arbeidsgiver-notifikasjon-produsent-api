package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgangerService

open class AltinnTilgangerServiceStub(
    val hentAlleTilgangerImpl: (String, String) -> AltinnTilganger = { _, _ -> AltinnTilganger(
        harFeil = false,
        tilganger = listOf()
    ) }
) : AltinnTilgangerService {
    constructor(vararg tilganger: Pair<String, AltinnTilganger>) : this(
        tilganger
            .toMap()
            .let {
                fun(fnr: String, _: String): AltinnTilganger {
                    return it[fnr] ?: AltinnTilganger(harFeil = false, tilganger = listOf())
                }
            }
    )

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
    ): AltinnTilganger =
        hentAlleTilgangerImpl(fnr, selvbetjeningsToken)
}
