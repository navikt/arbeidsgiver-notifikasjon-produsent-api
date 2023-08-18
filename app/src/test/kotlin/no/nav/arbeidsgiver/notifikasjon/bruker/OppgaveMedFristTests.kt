package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate

class OppgaveMedFristTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
        }
    )

    describe("oppgave med frist") {
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(
            frist = LocalDate.parse("2007-12-03"),
        )

        val oppgave = engine.queryNotifikasjonerJson()
            .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

        it("har frist") {
            oppgave.frist shouldBe oppgaveOpprettet.frist
        }
    }
})

