package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NærmesteLederService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NærmesteLederService.NærmesteLederFor

class NærmesteLederServiceStub(
    private val ansatte: List<NærmesteLederFor>
) : NærmesteLederService {

    constructor(): this(ansatte = listOf())

    constructor(vararg nærmesteLederFor: NærmesteLederFor): this(
        ansatte = nærmesteLederFor.toList()
    )

    constructor(vararg mottaker: Mottaker): this(
        *mottaker
            .filterIsInstance<NærmesteLederMottaker>()
            .map { NærmesteLederFor(
                ansattFnr = it.ansattFnr,
                virksomhetsnummer = it.virksomhetsnummer,
            )}
            .toTypedArray()
    )

    override suspend fun hentAnsatte(token: String): List<NærmesteLederFor> {
        return ansatte
    }
}