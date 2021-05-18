package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.VÅRE_TJENESTER

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    Main.main(
        httpPort = 8081,
        brukerAutentisering = LOCALHOST_AUTHENTICATION,
        produsentAutentisering = LOCALHOST_AUTHENTICATION,
        altinn = object: Altinn {
            override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<QueryModel.Tilgang> {
                val vnr = "1".repeat(9)
                return VÅRE_TJENESTER.map {
                    QueryModel.Tilgang(virksomhet = vnr, servicecode = it.first, serviceedition = it.second)
                }
            }
        }
    )
}

