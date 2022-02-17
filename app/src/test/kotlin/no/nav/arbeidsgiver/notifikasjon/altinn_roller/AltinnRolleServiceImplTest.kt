package no.nav.arbeidsgiver.notifikasjon.altinn_roller

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.AltinnRolleDefinisjon

class AltinnRolleServiceImplTest : DescribeSpec({
    val altinnRolleRepository = mockk<AltinnRolleRepository>()
    val subject = AltinnRolleServiceImpl(mockk(), altinnRolleRepository)
    describe("AltinnRolleService#hentAltinnrollerMedRetry") {
        beforeContainer {
            clearMocks(altinnRolleRepository)
        }
        context("når alle roller finnes i databasen") {
            coEvery { altinnRolleRepository.hentAltinnrolle(any()) } returns AltinnRolle("1", "1")
            val result = subject.hentAltinnrollerMedRetry(listOf(AltinnRolleDefinisjon("FOO")), 1, 0)

            it("returnerer liste av roller for gitte rolledefinisjon") {
                result shouldBe listOf(AltinnRolle("1", "1"))
            }
        }

        context("når roller finnes i databasen etterhvert") {
            coEvery {
                altinnRolleRepository.hentAltinnrolle(any())
            } throws RuntimeException("foo") andThen AltinnRolle("1", "1")
            val result = subject.hentAltinnrollerMedRetry(listOf(AltinnRolleDefinisjon("FOO")), 1, 0)

            it("returnerer liste av roller for gitte rolledefinisjon") {
                result shouldBe listOf(AltinnRolle("1", "1"))
            }

            it("hentAltinnrolle blir kalt to ganger") {
                coVerify(exactly = 2) {
                    altinnRolleRepository.hentAltinnrolle(any())
                }
            }
        }

        context("når roller ikke finnes i databasen") {
            coEvery {
                altinnRolleRepository.hentAltinnrolle(any())
            } throws RuntimeException("foo")

            it("throws") {
                shouldThrowAny {
                    subject.hentAltinnrollerMedRetry(listOf(AltinnRolleDefinisjon("FOO")), 2, 0)
                }
            }
        }
    }
})
