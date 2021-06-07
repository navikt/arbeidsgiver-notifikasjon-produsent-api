package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.QueryModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn

open class AltinnStub(
    val hentAlleTilgangerImpl: (String, String) -> List<QueryModel.Tilgang> = { _, _ -> listOf() }
) : Altinn {
    override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<QueryModel.Tilgang> =
        hentAlleTilgangerImpl(fnr, selvbetjeningsToken)
}
