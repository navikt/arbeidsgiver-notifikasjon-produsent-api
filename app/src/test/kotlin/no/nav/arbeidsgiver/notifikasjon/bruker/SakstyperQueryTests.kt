package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.TestApplicationEngine
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class SakstyperQueryTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub()
    )

    describe("ingen tilganger") {
        it("har ingen tilganger") {
            val foo = engine.querySakstyper()
            foo shouldBe emptyList()
        }
    }
})

private suspend fun TestApplicationEngine.querySakstyper(): List<String> =
    brukerApi("""
        query {
            sakstyper {
                navn
            }
        }
    """.trimIndent())
        .getTypedContent("$.sakstyper.*.navn")