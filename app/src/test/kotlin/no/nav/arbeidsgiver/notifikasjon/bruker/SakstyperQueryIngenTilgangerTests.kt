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

private val tilgang1 = HendelseModel.AltinnMottaker(
    virksomhetsnummer = "11111",
    serviceCode = "1",
    serviceEdition = "1"
)

class SakstyperQueryIngenTilgangerTests : DescribeSpec({

    describe("har ingen tilganger eller saker") {
        val (_, engine) = setupRepoOgEngine()
        it("får ingen sakstyper") {
            val sakstyper = engine.querySakstyper()
            sakstyper shouldBe emptySet()
        }
    }

    describe("har ingen tilganger, men finnes saker") {
        val (repo, engine) = setupRepoOgEngine()
        repo.sakOpprettet(
            virksomhetsnummer = tilgang1.virksomhetsnummer,
            mottakere = listOf<HendelseModel.Mottaker>(element = tilgang1),
        ).also {
            repo.nyStatusSak(
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

private fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)
    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub()
    )
    return Pair(brukerRepository, engine)
}


private fun TestApplicationEngine.querySakstyper(): Set<String> =
    querySakstyperJson()
        .getTypedContent<List<String>>("$.sakstyper.*.navn")
        .toSet()
