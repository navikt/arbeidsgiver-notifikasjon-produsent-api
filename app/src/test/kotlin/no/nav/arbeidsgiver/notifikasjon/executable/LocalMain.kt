package no.nav.arbeidsgiver.notifikasjon.executable

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.Main
import no.nav.arbeidsgiver.notifikasjon.QueryModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.VÅRE_TJENESTER
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_BRUKER_AUTHENTICATION
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_PRODUSENT_AUTHENTICATION

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    Main.main(
        httpPort = 8081,
        brukerAutentisering = listOf(
            HttpAuthProviders.FAKEDINGS_BRUKER,
            LOCALHOST_BRUKER_AUTHENTICATION,
        ),
        produsentAutentisering = listOf(
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
            LOCALHOST_PRODUSENT_AUTHENTICATION,
        ),
        altinn = object: Altinn {
            override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String): List<QueryModel.Tilgang> {
                val vnr = "811076732"
                return VÅRE_TJENESTER.map {
                    QueryModel.Tilgang(
                        virksomhet = vnr,
                        servicecode = it.code,
                        serviceedition = it.version
                    )
                }
            }
        }
    )
}

