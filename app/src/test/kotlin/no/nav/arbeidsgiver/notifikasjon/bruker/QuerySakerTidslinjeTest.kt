package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase


class QuerySakerTidslinjeTest: DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(
                listOf(
                    BrukerModel.Tilgang.Altinn(
                        virksomhet = "1",
                        servicecode = "1",
                        serviceedition = "1",
                    )
                )
            )
        }
    )

    describe("tidslinje") {
        // Lag to saker uten noen oppgaver/beskjeder

        it("tidslinje er tom til å starte med") {

        }

        // Legg til oppgave på en sak
        it("første oppgave vises på riktig sak") {

        }

        // Legg til beskjed på samme sak
        it("andre beskjed vises på riktig sak") {

        }

        // Legg til beskjed på andre sak
        it("tredje beskjed vises på riktig sak") {
        }
    }
})