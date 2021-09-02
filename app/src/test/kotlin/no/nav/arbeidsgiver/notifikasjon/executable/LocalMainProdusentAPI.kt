package no.nav.arbeidsgiver.notifikasjon.executable

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.FAGER_TESTPRODUSENT
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_PRODUSENT_AUTHENTICATION
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent as ProdusentDefinisjon


/* Produsent api */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    Produsent.main(
        httpPort = 8081,
        authProviders = listOf(
            HttpAuthProviders.FAKEDINGS_PRODUSENT,
            LOCALHOST_PRODUSENT_AUTHENTICATION,
        ),
        produsentRegister = object : ProdusentRegister {
            override fun finn(produsentid: String): ProdusentDefinisjon {
                return FAGER_TESTPRODUSENT
            }
        }
    )
}

