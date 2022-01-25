package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.util.*

class FeilhåndteringTests : DescribeSpec({
    val queryModel: BrukerModelImpl = mockk()

    val altinn: Altinn = mockk()

    val engine = ktorBrukerTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            enhetsregisteret = EnhetsregisteretStub("43" to "el virksomhete"),
            brukerModel = queryModel,
            kafkaProducer = mockk(),
        )
    )

    describe("graphql bruker-api feilhåndtering errors tilganger") {
        context("Feil Altinn, DigiSyfo ok") {

            coEvery {
                altinn.hentTilganger(any(), any(), any())
            } throws RuntimeException("Mock failure")

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
                coVerify { queryModel.hentNotifikasjoner(any(), listOf()) }
            }
        }
    }
})

