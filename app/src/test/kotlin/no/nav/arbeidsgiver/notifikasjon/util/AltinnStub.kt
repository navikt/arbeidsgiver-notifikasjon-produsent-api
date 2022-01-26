package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon

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

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>
    ): List<BrukerModel.Tilgang> =
        hentAlleTilgangerImpl(fnr, selvbetjeningsToken)

    override suspend fun hentRoller(): List<AltinnRolle> {
       return listOf(AltinnRolle("195", "DAGL"), AltinnRolle("196", "BOBE"))
    }
}
