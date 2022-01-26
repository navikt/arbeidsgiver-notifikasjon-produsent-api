package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class AltinnRollerTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentModel = ProdusentRepositoryImpl(database)

    describe("oppførsel Altinnroller") {

        context("altinnroller ginnes i tabell") {
            val roller = listOf(
                AltinnRolle("195", "DAGL"),
                AltinnRolle("196", "BOBE")
            )
            roller.forEach { rolle ->
                produsentModel.leggTilAltinnRolle(rolle)
            }
            it("DAGL er lagt inn i db") {
                val dagligLederFraDB = produsentModel.hentAltinnrolle("DAGL")
                dagligLederFraDB?.RoleDefinitionId shouldBe "195"
            }
            it("finner ikke ikkeeksisterende rolle") {
                val dagligLederFraDB = produsentModel.hentAltinnrolle("DOGL")
                dagligLederFraDB?.RoleDefinitionId shouldBe null
            }

            it("det går ikke ann å legge inn en eksisterende altinnrolle") {
                produsentModel.leggTilAltinnRolle(AltinnRolle("195", "DAGL"))
            }

        }
    }
})