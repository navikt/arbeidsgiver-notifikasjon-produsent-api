package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getGraphqlErrors
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class AltinnRollerTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)
    val altinn = AltinnStub()

    describe("oppførsel Altinnroller") {
        context("legg altinnroller i altinnrolletabell") {
            val roller = altinn.hentRoller()
            roller.forEach { rolle ->
               println("help help help")
                produsentModel.leggTilAltinnRolle(rolle)
            }
            it("DAGL er lagt inn i db") {
                val dagligLederFraDB = produsentModel.hentAltinnrolle("DAGL")
                dagligLederFraDB?.RoleDefinitionId shouldBe "195"
            }
        }
    }
})