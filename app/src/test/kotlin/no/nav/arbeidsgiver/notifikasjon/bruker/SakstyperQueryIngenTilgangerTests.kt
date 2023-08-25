package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime

private val tilgang1 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "11111",
    serviceCode = "1",
    serviceEdition = "1"
)

class SakstyperQueryIngenTilgangerTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub()
    )

    describe("har ingen tilganger eller saker") {
        it("får ingen sakstyper") {
            val sakstyper = engine.querySakstyper()
            sakstyper shouldBe emptySet()
        }
    }

    describe("har ingen tilganger, men finnes saker") {
        brukerRepository.sakOpprettet(
            virksomhetsnummer = tilgang1.virksomhetsnummer,
            mottakere = listOf<HendelseModel.Mottaker>(element = tilgang1),
        ).also {
            brukerRepository.nyStatusSak(
                sak = it,
                idempotensKey = IdempotenceKey.initial(),
            )
        }
        it("får ingen sakstyper") {
            val sakstyper = engine.querySakstyper()
            sakstyper shouldBe emptySet()
        }
    }
})


private fun TestApplicationEngine.querySakstyper(): Set<String> =
    querySakstyperJson()
        .getTypedContent<List<String>>("$.sakstyper.*.navn")
        .toSet()
