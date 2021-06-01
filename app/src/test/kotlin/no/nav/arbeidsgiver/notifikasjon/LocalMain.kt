package no.nav.arbeidsgiver.notifikasjon

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AuthConfigs
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.VÅRE_TJENESTER

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    Main.main(
        httpPort = 8081,
        brukerAutentisering = listOf(
            AuthConfigs.FAKEDINGS_BRUKER,
            LOCALHOST_BRUKER_AUTHENTICATION,
        ),
        produsentAutentisering = listOf(
            AuthConfigs.FAKEDINGS_PRODUSENT,
            LOCALHOST_PRODUSENT_AUTHENTICATION,
        ),
        altinn = object: Altinn {
            override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<QueryModel.Tilgang> {
                val vnr = "811076732"
                return VÅRE_TJENESTER.map {
                    QueryModel.Tilgang(
                        virksomhet = vnr,
                        servicecode = it.first,
                        serviceedition = it.second
                    )
                }
            }
        }
    )
}

