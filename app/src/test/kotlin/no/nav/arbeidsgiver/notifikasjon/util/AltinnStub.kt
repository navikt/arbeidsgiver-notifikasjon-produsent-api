package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon

open class AltinnStub(
    val hentAlleTilgangerImpl: (String, String) -> Tilganger = { _, _ -> Tilganger.EMPTY }
) : Altinn {
    constructor(vararg tilganger: Pair<String, Tilganger>) : this(
        tilganger
            .toMap()
            .let {
                fun(fnr: String, _: String): Tilganger {
                    return it[fnr] ?: Tilganger.EMPTY
                }
            }
    )

    override suspend fun hentTilganger(
        fnr: String,
        selvbetjeningsToken: String,
        tjenester: Iterable<ServicecodeDefinisjon>,
        roller: Iterable<AltinnRolle>,
    ): Tilganger =
        hentAlleTilgangerImpl(fnr, selvbetjeningsToken)
}
