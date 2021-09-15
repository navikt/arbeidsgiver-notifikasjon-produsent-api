package no.nav.arbeidsgiver.notifikasjon.executable

import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilgang
import no.nav.arbeidsgiver.notifikasjon.Bruker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NærmesteLederService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.MottakerRegister
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.EnhetsregisteretStub
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_BRUKER_AUTHENTICATION

/* Bruker API */
fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    Bruker.main(
        httpPort = 8082,
        authProviders = listOf(
            HttpAuthProviders.FAKEDINGS_BRUKER,
            LOCALHOST_BRUKER_AUTHENTICATION,
        ),
        enhetsregisteret = EnhetsregisteretStub(),
        nærmesteLederService = object : NærmesteLederService {
            override suspend fun hentAnsatte(userToken: String): List<NærmesteLederService.NærmesteLederFor> {
                return listOf()
            }
        },
        altinn = AltinnStub { _, _ ->
            val alleOrgnr = listOf(
                "811076732",
                "811076112",
                "922658986",
                "973610015",
                "991378642",
                "990229023",
                "810993472",
                "810993502",
                "910993542",
                "910825569",
                "910825550",
                "910825555",
                "999999999",
            )
            alleOrgnr.flatMap { orgnr ->
                MottakerRegister.servicecodeDefinisjoner.map { tjeneste ->
                    Tilgang(
                        virksomhet = orgnr,
                        servicecode = tjeneste.code,
                        serviceedition = tjeneste.version,
                    )
                }
            }
        }
    )
}

