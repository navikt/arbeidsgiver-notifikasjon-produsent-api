package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.BlockingAltinnClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon

class AltinnImplTests : DescribeSpec({
    val virksomhet1 = AltinnReportee(
        name = "virksomhet1",
        type = "Business",
        organizationNumber = "1",
        parentOrganizationNumber = null,
        organizationForm = null,
        status = null,
        socialSecurityNumber = null
    )
    val klient = object : BlockingAltinnClient {
        override fun hentOrganisasjoner(
            accessToken: Token,
            subject: Subject,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition,
            filtrerPåAktiveOrganisasjoner: Boolean
        ): List<AltinnReportee> = listOf(virksomhet1)

    }
    val altinn = AltinnImpl(klient)

    describe("Altinn#hentTilganger") {
        val def = ServicecodeDefinisjon("1", "1")
        val fnr = "42"

        val tilganger = altinn.hentTilganger(fnr, "token", listOf(def))

        it("returnerer tilganger") {
            tilganger.tjenestetilganger  shouldContainExactlyInAnyOrder listOf(
                BrukerModel.Tilgang.Altinn("1", def.code, def.version),
            )
        }

    }
})


