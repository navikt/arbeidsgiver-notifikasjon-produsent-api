package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.tid.inOsloAsInstant
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

class OppgaveEndrePåminnelseTests : DescribeSpec({
    val (brukerRepository, engine) = setupRepoOgEngine()

    describe("Oppgave får endret påminnelse") {
        val oppgaveOpprettetTidspunkt = OffsetDateTime.now()
        val oppgave = brukerRepository.oppgaveOpprettet(opprettetTidspunkt = oppgaveOpprettetTidspunkt)

        it("Oppgave har ingen påminnelse, men får en påminnelse opprettet") {

            val response1 = engine.queryNotifikasjonerJson()
            val harIkkePåminnelse =
                response1.getTypedContent<List<OffsetDateTime?>>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            harIkkePåminnelse shouldBe null
            val påminnelsesTidspunkt = oppgaveOpprettetTidspunkt.minusDays(2).toLocalDateTime()

            brukerRepository.oppgavePåminnelseEndret(
                oppgave.notifikasjonId,
                HendelseModel.Påminnelse(
                    tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(påminnelsesTidspunkt, påminnelsesTidspunkt.inOsloAsInstant()),
                    eksterneVarsler = emptyList()
                )
            )
            val response2 = engine.queryNotifikasjonerJson()
            val harPåminnelse = response2.getTypedContent<List<OffsetDateTime?>>("$.notifikasjoner.notifikasjoner[0].paaminnelseTidspunkt")
            harPåminnelse.count() shouldBe 1
        }
    }
})

private suspend fun BrukerRepository.oppgavePåminnelseEndret(oppgaveId: UUID, paaminnelse: HendelseModel.Påminnelse?) {
    HendelseModel.OppgavePaaminnelseEndret(
        virksomhetsnummer = "42",
        hendelseId = UUID.randomUUID(),
        påminnelse = paaminnelse,
        notifikasjonId = oppgaveId,
        kildeAppNavn = "1",
        produsentId = "1",
        frist = null
    ).also {
        oppdaterModellEtterHendelse(it)
    }
}


private fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)
    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
        }
    )
    return Pair(brukerRepository, engine)
}

