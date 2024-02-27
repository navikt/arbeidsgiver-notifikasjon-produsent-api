package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.NY
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.UTFOERT
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.LocalDate
import java.time.OffsetDateTime

class OppgaveMedFristTests : DescribeSpec({

    describe("oppgave med frist") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(
            frist = LocalDate.parse("2007-12-03"),
        )

        val oppgave = engine.queryNotifikasjonerJson()
            .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

        it("har frist") {
            oppgave.frist shouldBe oppgaveOpprettet.frist
        }
    }

    describe("ny oppgave med utsatt frist får ny frist") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(
            frist = LocalDate.parse("2007-12-03"),
        )
        val fristUtsatt = brukerRepository.oppgaveFristUtsatt(
            oppgaveOpprettet,
            frist = LocalDate.parse("2009-09-09")
        )

        val oppgave = engine.queryNotifikasjonerJson()
            .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

        it("har opprinnelig frist") {
            oppgave.frist shouldBe fristUtsatt.frist
            oppgave.tilstand shouldBe NY
        }
    }

    describe("utført oppgave med utsatt frist har samme frist") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(
            frist = LocalDate.parse("2007-12-03"),
        )
        brukerRepository.oppgaveUtført(oppgaveOpprettet)
        brukerRepository.oppgaveFristUtsatt(
            oppgaveOpprettet,
            frist = LocalDate.parse("2009-09-09")
        )

        val oppgave = engine.queryNotifikasjonerJson()
            .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

        it("har opprinnelig frist") {
            oppgave.frist shouldBe oppgaveOpprettet.frist
            oppgave.tilstand shouldBe UTFOERT
        }
    }

    describe("utgått oppgave med utsatt frist har ny frist") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(
            frist = LocalDate.parse("2007-12-03"),
        )
        brukerRepository.oppgaveUtgått(oppgaveOpprettet, utgaattTidspunkt = OffsetDateTime.parse("2008-08-08T08:08:08Z"))
        val fritUtsatt = brukerRepository.oppgaveFristUtsatt(
            oppgaveOpprettet,
            frist = LocalDate.parse("2009-09-09")
        )

        val oppgave = engine.queryNotifikasjonerJson()
            .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

        it("har ny frist") {
            oppgave.frist shouldBe fritUtsatt.frist
            oppgave.utgaattTidspunkt shouldBe null
            oppgave.tilstand shouldBe NY
        }
    }
})

