package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.SuspendingAltinnClient
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer

class FeilhåndteringTests : DescribeSpec({
    val queryModel: BrukerRepositoryImpl = mockk()
    val suspendingAltinnClient = mockk<SuspendingAltinnClient>()

    val engine = ktorBrukerTestServer(
        altinn = AltinnImpl(suspendingAltinnClient),
        brukerRepository = queryModel,
    )

    describe("graphql bruker-api feilhåndtering errors tilganger") {
        context("Feil Altinn, DigiSyfo ok") {
            coEvery {
                suspendingAltinnClient.hentOrganisasjoner(any(), any(), any(), any(), any())
            } returns null
            coEvery {
                suspendingAltinnClient.hentOrganisasjoner(any(), any(), any())
            } returns null
            coEvery {
                suspendingAltinnClient.hentReportees(any(), any())
            } returns null
            coEvery {
                queryModel.hentNotifikasjoner(any(), any())
            } returns listOf()

            val response = engine.brukerApi(
                """
                    {
                        notifikasjoner{
                            feilAltinn
                            feilDigiSyfo                    
                        }
                    }
                """.trimIndent()
            )

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }

            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            it("feil Altinn") {
                response.getTypedContent<Boolean>("notifikasjoner/feilAltinn") shouldBe true
                response.getTypedContent<Boolean>("notifikasjoner/feilDigiSyfo") shouldBe false
                coVerify { queryModel.hentNotifikasjoner(any(), Tilganger.FAILURE) }
            }
        }
    }
})

