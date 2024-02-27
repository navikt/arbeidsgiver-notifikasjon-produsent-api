package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime

class OppgaveUførtTests : DescribeSpec({

    describe("oppgave utført") {
        val database = testDatabase(Bruker.databaseConfig)
        val brukerRepository = BrukerRepositoryImpl(database)
        val engine = ktorBrukerTestServer(
            brukerRepository = brukerRepository,
            altinn = AltinnStub { _, _ ->
                BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
            }
        )
        val oppgaveOpprettet = brukerRepository.oppgaveOpprettet(
            notifikasjonId = uuid("0"),
            opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
        )
        val oppgaveUtført = brukerRepository.oppgaveUtført(
            oppgaveOpprettet,
            utfoertTidspunkt = OffsetDateTime.parse("2018-12-03T10:15:30+01:00"),
        )
        brukerRepository.oppdaterModellEtterHendelse(oppgaveUtført)

        val oppgave = engine.queryNotifikasjonerJson()
            .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("notifikasjoner/notifikasjoner/0")

        it("har tilstand utført og utført tidspunkt") {
            oppgave.tilstand shouldBe BrukerAPI.Notifikasjon.Oppgave.Tilstand.UTFOERT
            oppgave.utfoertTidspunkt shouldNotBe null
            oppgave.utfoertTidspunkt!!.toInstant() shouldBe oppgaveUtført.utfoertTidspunkt?.toInstant()
        }
    }
})
