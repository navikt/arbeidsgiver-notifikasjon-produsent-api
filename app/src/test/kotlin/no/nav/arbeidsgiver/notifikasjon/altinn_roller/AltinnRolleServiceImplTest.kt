package no.nav.arbeidsgiver.notifikasjon.altinn_roller

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.AltinnRolleDefinisjon

class AltinnRolleServiceImplTest : DescribeSpec({
    val altinnRolleRepository = mockk<AltinnRolleRepository>()
    val subject = AltinnRolleServiceImpl(mockk(), altinnRolleRepository)
    describe("AltinnRolleService#hentRoller") {
        coEvery {
            altinnRolleRepository.hentAlleAltinnRoller()
        } returns listOf(AltinnRolle("1", "FOO"))

        context("når rolle for gitt kode finnes i databasen") {
            it("returnerer liste av roller for gitte rolledefinisjon") {
                val result = subject.hentRoller(listOf(AltinnRolleDefinisjon("FOO")))
                result shouldBe listOf(AltinnRolle("1", "FOO"))
            }
        }

        context("når rolle for gitt kode ikke finnes i databasen") {
            it("throws") {
                shouldThrowAny {
                    subject.hentRoller(listOf(AltinnRolleDefinisjon("BAR")))
                }
            }
        }
    }
})
