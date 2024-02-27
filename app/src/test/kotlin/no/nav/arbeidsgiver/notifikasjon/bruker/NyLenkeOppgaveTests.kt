package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase


class NyLenkeOppgaveTests: DescribeSpec({

    describe("Oppgave med uendret lenke") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(lenke = "https://opprettelse-lenke")
        it("Starter med lenken fra hendelse") {
            brukerRepository.hentLenke() shouldBe "https://opprettelse-lenke"
        }

        brukerRepository.oppgaveUtført(oppgaveOpprettet, nyLenke = null)
        it("Fortsatt med lenke fra opprettelse") {
            brukerRepository.hentLenke() shouldBe "https://opprettelse-lenke"
        }
    }

    describe("Oppgave med endret lenke") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(lenke = "https://opprettelse-lenke")
        it("Starter med lenken fra hendelse") {
            brukerRepository.hentLenke() shouldBe "https://opprettelse-lenke"
        }

        brukerRepository.oppgaveUtført(oppgaveOpprettet, nyLenke = "https://utført-lenke")
        it("Ny lenke fra utført") {
            brukerRepository.hentLenke() shouldBe "https://utført-lenke"
        }
    }
})

private suspend fun BrukerRepository.hentLenke() =
    hentNotifikasjoner(
        fnr = "",
        tilganger = BrukerModel.Tilganger(tjenestetilganger = listOf(TEST_TILGANG_1))
    )
        .filterIsInstance<BrukerModel.Oppgave>()
        .first()
        .lenke

