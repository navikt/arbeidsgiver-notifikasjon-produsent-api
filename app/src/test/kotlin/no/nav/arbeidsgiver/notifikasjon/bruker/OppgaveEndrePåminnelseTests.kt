package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime
import java.util.*

class OppgaveEndrePåminnelseTests : DescribeSpec({
    val (brukerRepository, engine) = setupRepoOgEngine()

    describe("Oppgave får endret påminnelse") {
        val oppgave = brukerRepository.oppgaveOpprettet()

        it("Oppgave har ingen påminnelse, men får en påminnelse opprettet")
            val response1 = engine.queryNotifikasjonerJson()
            val harPåminnelse =
                response1.getTypedContent<List<OffsetDateTime?>>("$.notifikasjoner.notifikasjoner[*].paaminnelseTidspunkt")
                    .map { it != null }
            harPåminnelse shouldBe listOf(false)

        brukerRepository.oppgavePåminnelseEndret(oppgave.notifikasjonId,
            HendelseModel.Påminnelse(
        ))
    }
})

private suspend fun BrukerRepository.oppgavePåminnelseEndret(oppgaveId: UUID, paaminnelse: HendelseModel.Påminnelse?) {
    HendelseModel.OppgavePaaminnelseEndret(
        virksomhetsnummer = "42",
        hendelseId = UUID.randomUUID(),
        påminnelse = paaminnelse,
        notifikasjonId = oppgaveId,
        kildeAppNavn = "1",
        produsentId = "1"
    ).also {
        oppdaterModellEtterHendelse(it)
    }
}


private fun DescribeSpec.setupRepoOgEngine(): Pair<BrukerRepositoryImpl, TestApplicationEngine> {
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)
    val engine = ktorBrukerTestServer(
        altinn = AltinnStub(
            "0".repeat(11) to BrukerModel.Tilganger(
                tjenestetilganger = listOf(
                    BrukerModel.Tilgang.Altinn("42", "5441", "1"),
                    BrukerModel.Tilgang.Altinn("43", "5441", "1")
                ),
            )
        ),
        brukerRepository = brukerRepository,
    )
    return Pair(brukerRepository, engine)
}

