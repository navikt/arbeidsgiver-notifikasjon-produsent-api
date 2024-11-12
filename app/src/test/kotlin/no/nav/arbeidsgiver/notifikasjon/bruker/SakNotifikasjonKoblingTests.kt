package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.util.AltinnTilgangerServiceStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class SakNotifikasjonKoblingTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)
    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinnTilgangerService = AltinnTilgangerServiceStub { _, _ ->
            AltinnTilganger(
                harFeil = false,
                tilganger = listOf(TEST_TILGANG_1)
            )
        }
    )

    describe("sak og oppgave med samme grupperingsid, men forskjellig merkelapp ") {
        brukerRepository.oppgaveOpprettet(
            grupperingsid = "gruppe1",
            merkelapp = "X",

        )
        brukerRepository.sakOpprettet(
            grupperingsid = "gruppe1",
            merkelapp = "Z",
        ).also {
            brukerRepository.nyStatusSak(sak = it, idempotensKey = "")
        }

        it ("sak og oppgave eksisterer hver for seg") {
            val saker = engine.querySakerJson()
                .getTypedContent<List<Any>>("$.saker.saker")
            saker should haveSize(1)

            val notifikasjoner = engine.queryNotifikasjonerJson()
                .getTypedContent<List<Any>>("$.notifikasjoner.notifikasjoner")
            notifikasjoner should haveSize(1)
        }

        it("oppgave dukker ikke opp i tidslinje") {
            val tidslinje = engine.querySakerJson()
                .getTypedContent<List<Any>>("$.saker.saker[0].tidslinje")
            tidslinje should beEmpty()
        }

        it("sak dukker ikke opp på oppgave") {
            val oppgave = engine.queryNotifikasjonerJson()
                .getTypedContent<BrukerAPI.Notifikasjon.Oppgave>("$.notifikasjoner.notifikasjoner[0]")
            oppgave.sak should beNull()
        }
    }
})