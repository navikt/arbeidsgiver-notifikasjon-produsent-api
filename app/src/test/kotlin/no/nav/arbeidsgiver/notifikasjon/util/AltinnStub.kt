package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn

open class AltinnStub(
    val hentAlleTilgangerImpl: (String, String) -> List<BrukerModel.Tilgang> = { _, _ -> listOf() }
) : Altinn {
    constructor(vararg tilganger: Pair<String, List<BrukerModel.Tilgang>>) : this(
        tilganger
            .toMap()
            .let {
                fun (fnr: String, _: String): List<BrukerModel.Tilgang> {
                    return it[fnr] ?: emptyList()
                }
            }
    )

    override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<BrukerModel.Tilgang> =
        hentAlleTilgangerImpl(fnr, selvbetjeningsToken)
}
