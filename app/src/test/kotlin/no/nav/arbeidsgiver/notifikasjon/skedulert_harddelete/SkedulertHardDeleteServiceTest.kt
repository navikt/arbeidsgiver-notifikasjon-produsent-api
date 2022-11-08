package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem.AUTOSLETT_SERVICE
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class SkedulertHardDeleteServiceTest : DescribeSpec({

    val kafkaProducer = FakeHendelseProdusent()
    val repo = mockk<SkedulertHardDeleteRepository>()
    val service = SkedulertHardDeleteService(repo, kafkaProducer)
    val nåTidspunkt = Instant.parse("2020-01-01T20:20:01.01Z")

    afterSpec {
        Health.subsystemAlive[AUTOSLETT_SERVICE] = true
    }

    describe("AutoSlettService#slettDeSomSkalSlettes") {
        context("når de som skal slettes er gyldig") {
            val skalSlettes = listOf(
                skedulertHardDelete(uuid("1")),
                skedulertHardDelete(uuid("2")),
            )
            coEvery { repo.hentDeSomSkalSlettes(any()) } returns skalSlettes

            service.slettDeSomSkalSlettes(nåTidspunkt)

            it("sender hardDelete for aggregater som skal slettes") {
                val hardDeletes = kafkaProducer.hendelserOfType<HendelseModel.HardDelete>()
                val deletedIds = hardDeletes.map(HendelseModel.HardDelete::aggregateId)
                val expected = listOf(uuid("1"), uuid("2"))

                deletedIds shouldContainExactlyInAnyOrder expected
            }
        }

        context("når de som skal slettes inneholder noe som skal slettes i fremtiden") {
            val skalSlettes = listOf(
                skedulertHardDelete(uuid("1"), nåTidspunkt - Duration.ofSeconds(1)),
                skedulertHardDelete(uuid("2"), nåTidspunkt + Duration.ofSeconds(1)),
            )
            coEvery { repo.hentDeSomSkalSlettes(any()) } returns skalSlettes

            it("validering feiler og metoden kaster") {
                service.slettDeSomSkalSlettes(nåTidspunkt)

                Health.subsystemAlive[AUTOSLETT_SERVICE] shouldBe false
            }
        }
    }
})

private fun skedulertHardDelete(
    aggregateId: UUID,
    beregnetSlettetidspunkt: Instant = Instant.EPOCH
) = SkedulertHardDeleteRepository.SkedulertHardDelete(
    aggregateId = aggregateId,
    aggregateType = "foo",
    virksomhetsnummer = "21",
    produsentid = "test",
    merkelapp = "tag",
    inputBase = OffsetDateTime.now(),
    inputOm = null,
    inputDen = LocalDateTime.now(),
    beregnetSlettetidspunkt = beregnetSlettetidspunkt,
)
