package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Brreg
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.BrregEnhet

open class BrregStub(
    val finnNavn: (String) -> String? = { "ARBEIDS- OG VELFERDSETATEN" }
) : Brreg {

    constructor(first: Pair<String, String>, vararg mapping: Pair<String, String>) : this((listOf(first) + mapping.toList()).toMap()::get)

    override suspend fun hentEnhet(orgnr: String): BrregEnhet =
        finnNavn(orgnr)?.let { navn ->
            BrregEnhet(
                organisasjonsnummer = orgnr,
                navn = navn
            )
        }
            ?: throw Error("Ingen navn registrert for $orgnr")
}
