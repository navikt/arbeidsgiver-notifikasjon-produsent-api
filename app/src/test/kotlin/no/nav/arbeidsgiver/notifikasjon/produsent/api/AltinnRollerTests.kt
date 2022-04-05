package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.Produsent
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleClient
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleServiceImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class AltinnRollerTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepo = ProdusentRepositoryImpl(database)
    val rolleRepository = produsentRepo.altinnRolle
    val altinnRolleClient = mockk<AltinnRolleClient>()
    val altinnservice = AltinnRolleServiceImpl(altinnRolleClient, rolleRepository)

    describe("oppførsel Altinnroller") {
        val altinnRoller = listOf(AltinnRolle("195", "DAGL"), AltinnRolle("196", "BOBE"))
        coEvery { altinnRolleClient.hentRoller() } returns altinnRoller

        context("altinnroller har ikke blitt lastet til tabell") {

            it("altinnservice legger 2 roller inn i db uten å kræsje") {
                altinnservice.lastRollerFraAltinn()
                rolleRepository.hentAlleAltinnRoller().size shouldBe 2
            }

            it("altinnservice legger ikke eksisterende roller inn i db") {
                altinnservice.lastRollerFraAltinn()
                rolleRepository.hentAlleAltinnRoller().size shouldBe 2
            }

            it("finner ikke ikkeeksisterende rolle") {
                val dagligLederFraDB = rolleRepository.hentAltinnrolle("DOGL")
                dagligLederFraDB?.RoleDefinitionId shouldBe null
            }

            it("feil hvis man prøver å legg inn eksisterende rolleid med ny rollekode") {
                shouldThrowAny { rolleRepository.leggTilAltinnRoller(listOf(AltinnRolle("195", "DOGL"))) }
            }

            it("feil hvis man prøver å legg inn eksisterende rollekode med ny rolleid") {
                shouldThrowAny { rolleRepository.leggTilAltinnRoller(listOf(AltinnRolle("197", "DAGL"))) }
            }

            it("altinnservice kaster en feil når en rolleid har fått ny rollekode") {
                coEvery { altinnRolleClient.hentRoller() } returns listOf(AltinnRolle("195", "DOGL"))
                shouldThrowAny { altinnservice.lastRollerFraAltinn() }
            }
        }
    }
})