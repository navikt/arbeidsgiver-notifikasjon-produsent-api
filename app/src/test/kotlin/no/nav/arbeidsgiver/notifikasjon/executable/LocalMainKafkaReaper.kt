package no.nav.arbeidsgiver.notifikasjon.executable

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilgang
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.KafkaReaper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.VÅRE_TJENESTER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.EnhetsregisteretStub
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_BRUKER_AUTHENTICATION

/* Bruker API */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    KafkaReaper.main(
        httpPort = 8083
    )
}

