package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret

open class EnhetsregisteretStub(
    val finnNavn: (String) -> String? = { "ARBEIDS- OG VELFERDSETATEN" }
) : Enhetsregisteret {

    constructor(first: Pair<String, String>, vararg mapping: Pair<String, String>) : this((listOf(first) + mapping.toList()).toMap()::get)

    override suspend fun hentUnderenhet(orgnr: String): Enhetsregisteret.Underenhet =
        finnNavn(orgnr)?.let { navn ->
            Enhetsregisteret.Underenhet(
                organisasjonsnummer = orgnr,
                navn = navn
            )
        }
            ?: throw Error("Ingen navn registrert for $orgnr")
}
