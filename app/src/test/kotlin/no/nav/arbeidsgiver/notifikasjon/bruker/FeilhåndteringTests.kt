package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import java.util.*

interface BrukerRepositoryStub : BrukerRepository {
    override suspend fun hentNotifikasjoner(fnr: String, tilganger: Tilganger): List<BrukerModel.Notifikasjon> {
        TODO("Not yet implemented")
    }
    override suspend fun hentSaker(
        fnr: String,
        virksomhetsnummer: List<String>,
        tilganger: Tilganger,
        tekstsoek: String?,
        sakstyper: List<String>?,
        offset: Int,
        limit: Int,
        sortering: BrukerAPI.SakSortering,
        oppgaveTilstand: List<BrukerModel.Oppgave.Tilstand>?
    ) = TODO("Not yet implemented")
    override suspend fun berikSaker(saker: List<BrukerModel.Sak>) = TODO("Not yet implemented")
    override suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, metadata: HendelseModel.HendelseMetadata) = TODO("Not yet implemented")
    override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID) = TODO("Not yet implemented")
    override suspend fun oppdaterModellEtterNærmesteLederLeesah(nærmesteLederLeesah: NarmesteLederLeesah) = TODO("Not yet implemented")
    override suspend fun hentSakstyper(fnr: String, tilganger: Tilganger) = TODO("Not yet implemented")
    override suspend fun hentSakerForNotifikasjoner(grupperinger: List<BrukerModel.Gruppering>) : Map<String, String> = TODO("Not yet implemented")
}

class FeilhåndteringTests : DescribeSpec({

    val engine = ktorBrukerTestServer(
        altinn = AltinnStub { _, _ -> Tilganger.FAILURE },

        brukerRepository = object : BrukerRepositoryStub {
            override suspend fun hentNotifikasjoner(
                fnr: String, tilganger: Tilganger
            ) = emptyList<BrukerModel.Notifikasjon>()

            override suspend fun hentSakerForNotifikasjoner(
                grupperinger: List<BrukerModel.Gruppering>
            ) = emptyMap<String, String>()
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

