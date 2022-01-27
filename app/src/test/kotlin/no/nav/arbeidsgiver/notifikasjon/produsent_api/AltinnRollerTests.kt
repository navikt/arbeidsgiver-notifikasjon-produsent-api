package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class AltinnRollerTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepo = ProdusentRepositoryImpl(database)
    val altinn = mockk<Altinn>()
    val altinnservice = AltinnRolleServiceImpl(altinn, produsentRepo)

    describe("oppførsel Altinnroller") {
        val altinnRoller = listOf(AltinnRolle("195", "DAGL"), AltinnRolle("196", "BOBE"))
        coEvery { altinn.hentRoller() } returns altinnRoller

        context("altinnroller har ikke blitt lastet til tabell") {

            it("altinnservice legger 2 roller inn i db uten å kræsje") {
                altinnservice.lastAltinnroller()
                produsentRepo.hentAlleAltinnRoller().size shouldBe 2
            }

            it("altinnservice legger ikke eksisterende roller inn i db") {
                altinnservice.lastAltinnroller()
                produsentRepo.hentAlleAltinnRoller().size shouldBe 2
            }

            it("finner ikke ikkeeksisterende rolle") {
                val dagligLederFraDB = produsentRepo.hentAltinnrolle("DOGL")
                dagligLederFraDB?.RoleDefinitionId shouldBe null
            }

            it("feil hvis man prøver å legg inn eksisterende rolleid med ny rollekode") {
                shouldThrowAny { produsentRepo.leggTilAltinnRolle(AltinnRolle("195", "DOGL")) }
            }

            it("feil hvis man prøver å legg inn eksisterende rollekode med ny rolleid") {
                shouldThrowAny { produsentRepo.leggTilAltinnRolle(AltinnRolle("197", "DAGL")) }
            }

            it("altinnservice kaster en feil når en rolleid har fått ny rollekode") {
                val altinnRoller = listOf(AltinnRolle("195", "DOGL"))
                coEvery { altinn.hentRoller() } returns altinnRoller
                shouldThrowAny { altinnservice.lastAltinnroller() }
            }
        }
    }
})