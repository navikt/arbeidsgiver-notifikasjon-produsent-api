package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.SuspendingAltinnClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon

@Suppress("HttpUrlsUsage")
class AltinnImplTests : DescribeSpec({
    val klient = mockk<SuspendingAltinnClient>()
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
        // TODO: legg til roller

        val tilganger = altinn.hentTilganger(fnr, "token", listOf(def), listOf())

        it("returnerer tilganger") {
            tilganger.tjenestetilganger + tilganger.reportee + tilganger.rolle shouldContainExactlyInAnyOrder listOf(
                BrukerModel.Tilgang.Altinn("1", def.code, def.version),
                BrukerModel.Tilgang.AltinnReportee(fnr = fnr, virksomhet = "2")
            )
        }

    }
})


