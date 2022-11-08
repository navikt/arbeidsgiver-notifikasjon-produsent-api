package no.nav.arbeidsgiver.notifikasjon.executable.produsent_api

import no.nav.arbeidsgiver.notifikasjon.executable.Port
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent as ProdusentMain
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.FAGER_TESTPRODUSENT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_PRODUSENT_AUTHENTICATION


/* Produsent api */
fun main() {
    ProdusentMain.main(
        httpPort = Port.PRODUSENT_API.port,
        authProviders = listOf(
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
            LOCALHOST_PRODUSENT_AUTHENTICATION,
        ),
        produsentRegister = object : ProdusentRegister {
            override fun finn(appName: String): Produsent {
                return FAGER_TESTPRODUSENT
            }
        }
    )
}

