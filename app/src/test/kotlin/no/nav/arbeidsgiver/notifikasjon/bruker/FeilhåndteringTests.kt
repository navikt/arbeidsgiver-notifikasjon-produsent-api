package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.*

class FeilhåndteringTests : DescribeSpec({

    val engine = ktorBrukerTestServer(
        altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
            AltinnTilganger(
                harFeil = true,
                tilganger = listOf()
            )
        },

        brukerRepository = object : BrukerRepositoryStub() {
            override suspend fun hentNotifikasjoner(
                fnr: String, altinnTilganger: AltinnTilganger
            ) = emptyList<BrukerModel.Notifikasjon>()

            override suspend fun hentSakerForNotifikasjoner(
                grupperinger: List<BrukerModel.Gruppering>
            ) = emptyMap<String, BrukerModel.SakMetadata>()
        },
    )

    describe("graphql bruker-api feilhåndtering errors tilganger") {
        context("Feil Altinn, DigiSyfo ok") {
            val response = engine.queryNotifikasjonerJson()

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }

            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            it("feil Altinn") {
                response.getTypedContent<Boolean>("notifikasjoner/feilAltinn") shouldBe true
                response.getTypedContent<Boolean>("notifikasjoner/feilDigiSyfo") shouldBe false
            }
        }
    }
})

