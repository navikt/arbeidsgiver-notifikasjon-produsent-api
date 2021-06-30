package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret

open class EnhetsregisteretStub(
    val finnNavn: (String) -> String? = { "ARBEIDS- OG VELFERDSETATEN" }
) : Enhetsregisteret {

    constructor(first: Pair<String, String>, vararg mapping: Pair<String, String>) : this((listOf(first) + mapping.toList()).toMap()::get)

    override suspend fun hentEnhet(orgnr: String): Enhetsregisteret.Enhet =
        finnNavn(orgnr)?.let { navn ->
            Enhetsregisteret.Enhet(
                organisasjonsnummer = orgnr,
                navn = navn
            )
        }
            ?: throw Error("Ingen navn registrert for $orgnr")
}
