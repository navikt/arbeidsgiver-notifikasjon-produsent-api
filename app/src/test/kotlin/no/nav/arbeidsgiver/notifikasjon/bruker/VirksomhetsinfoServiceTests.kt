package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Enhetsregisteret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.SuspendingAltinnClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.tokenx.TokenXClientStub

class VirksomhetsinfoServiceTests: DescribeSpec({
    val enhetsregisteret = object: Enhetsregisteret {
        override suspend fun hentUnderenhet(orgnr: String): Enhetsregisteret.Underenhet {
            return Enhetsregisteret.Underenhet(orgnr, "ereg $orgnr")
        }
    }

    val virksomhetsinfoService = VirksomhetsinfoService(
        enhetsregisteret = enhetsregisteret,
    )

    val altinnrettigheteterProxyKlient = mockk<AltinnrettigheterProxyKlient>()

    coEvery {
        altinnrettigheteterProxyKlient.hentOrganisasjoner(any(), any(), any(), any(), any())
    } returns listOf(
        baseAltinnReportee.copy(
            name = "altinn 1",
            organizationNumber = "1"
        ),
        baseAltinnReportee.copy(
            name = "altinn 2",
            organizationNumber = "2",
        ),
        baseAltinnReportee.copy(
            name = "altinn 3",
            organizationNumber = "3",
        )
    )

    val altinn = SuspendingAltinnClient(
        blockingClient = altinnrettigheteterProxyKlient,
        observer = virksomhetsinfoService::altinnObserver,
        tokenXClient = TokenXClientStub(),
    )

    describe("automatisk caching av virksomhetsnavn fra altinn-kall") {
        it("ingen cache på navnet") {
            virksomhetsinfoService.hentUnderenhet("1").navn shouldBe "ereg 1"
            virksomhetsinfoService.hentUnderenhet("2").navn shouldBe "ereg 2"
        }

        it("caching av navn forekommer") {
            altinn.hentOrganisasjoner(SelvbetjeningToken(""), Subject(""), ServiceCode(""), ServiceEdition(""), false)
            virksomhetsinfoService.hentUnderenhet("1").navn shouldBe "altinn 1"
            virksomhetsinfoService.hentUnderenhet("2").navn shouldBe "altinn 2"
            virksomhetsinfoService.hentUnderenhet("3").navn shouldBe "altinn 3"
            virksomhetsinfoService.hentUnderenhet("4").navn shouldBe "ereg 4"
        }
    }
})

private val baseAltinnReportee = AltinnReportee(
    name = "",
    type = "",
    parentOrganizationNumber = null,
    organizationNumber = "",
    organizationForm = null,
    status = null,
    socialSecurityNumber = null,
)
