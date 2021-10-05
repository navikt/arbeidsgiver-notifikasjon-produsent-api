package no.nav.arbeidsgiver.notifikasjon.executable

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.Produsent as ProdusentMain
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.FAGER_TESTPRODUSENT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_PRODUSENT_AUTHENTICATION


/* Produsent api */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    ProdusentMain.main(
        httpPort = 8081,
        authProviders = listOf(
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
            LOCALHOST_PRODUSENT_AUTHENTICATION,
        ),
        produsentRegister = object : ProdusentRegister {
            override fun finn(appName: String): Produsent {
                return Produsent(appName, FAGER_TESTPRODUSENT)
            }
        }
    )
}

