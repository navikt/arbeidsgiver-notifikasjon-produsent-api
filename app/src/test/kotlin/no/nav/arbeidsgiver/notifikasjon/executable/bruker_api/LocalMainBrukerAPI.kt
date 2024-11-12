package no.nav.arbeidsgiver.notifikasjon.executable.bruker_api

import no.nav.arbeidsgiver.notifikasjon.bruker.Bruker
import no.nav.arbeidsgiver.notifikasjon.executable.Port
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilgang
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.HttpAuthProviders
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.MottakerRegister
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.EnhetsregisteretStub
import no.nav.arbeidsgiver.notifikasjon.util.LOCALHOST_BRUKER_AUTHENTICATION

/* Bruker API */
fun main() {
    Bruker.main(
        httpPort = Port.BRUKER_API.port,
        authProviders = listOf(
            HttpAuthProviders.FAKEDINGS_BRUKER,
            LOCALHOST_BRUKER_AUTHENTICATION,
        ),
        enhetsregisteret = EnhetsregisteretStub(),
        altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
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
            AltinnTilganger(
                harFeil = false,
                tilganger = alleOrgnr.flatMap { orgnr ->
                    MottakerRegister.servicecodeDefinisjoner.map { tjeneste ->
                        AltinnTilgang(
                            orgNr = orgnr,
                            tilgang = "${tjeneste.code}:${tjeneste.version}"
                        )
                    }
                }
            )
        }
    )
}

