package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.QueryModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn

open class AltinnStub(
    val hentAlleTilgangerImpl: (String, String) -> List<QueryModel.Tilgang> = { _, _ -> listOf() }
) : Altinn {
    constructor(vararg tilganger: Pair<String, List<QueryModel.Tilgang>>) : this(
        tilganger
            .toMap()
            .let {
                fun (fnr: String, _: String): List<QueryModel.Tilgang> {
                    return it[fnr] ?: emptyList()
                }
            }
    )

    override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<QueryModel.Tilgang> =
        hentAlleTilgangerImpl(fnr, selvbetjeningsToken)
}
