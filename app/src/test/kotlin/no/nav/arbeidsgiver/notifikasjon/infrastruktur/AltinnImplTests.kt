package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.unblocking.NonBlockingAltinnrettigheterProxyKlient

@Suppress("HttpUrlsUsage")
class AltinnImplTests : DescribeSpec({
    val klient = mockk<NonBlockingAltinnrettigheterProxyKlient>()
    val altinn = AltinnImpl(klient)

    describe("Altinn#hentTilganger") {
        val def = ServicecodeDefinisjon("1", "1")
        val fnr = "42"
        val virksomhet1 = AltinnReportee(
            name = "virksomhet1",
            type = "Business",
            organizationNumber = "1",
            parentOrganizationNumber = null,
            organizationForm = null,
            status = null,
            socialSecurityNumber = null
        )
        val virksomhet2 = AltinnReportee(
            name = "virksomhet2",
            type = "Business",
            organizationNumber = "2",
            parentOrganizationNumber = null,
            organizationForm = null,
            status = null,
            socialSecurityNumber = null
        )
        coEvery {
            klient.hentOrganisasjoner(
                any(),
                Subject(fnr),
                ServiceCode(def.code),
                ServiceEdition(def.version),
                false
            )
        } returns listOf(virksomhet1)
        coEvery {
            klient.hentOrganisasjoner(
                any(),
                Subject(fnr),
                true
            )
        } returns listOf(virksomhet2)

        val tilganger = altinn.hentTilganger(fnr, "token", listOf(def))

        it("returnerer tilganger") {
            tilganger.size shouldBe 2
            tilganger.first() shouldBe BrukerModel.Tilgang.Altinn("1", def.code, def.version)
            tilganger.last() shouldBe BrukerModel.Tilgang.AltinnReportee(fnr = fnr, virksomhet = "2")
        }

    }
})


