package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem.AUTOSLETT_SERVICE
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.AggregateType.Oppgave
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.AggregateType.Sak
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

    describe("HardDeleteService#prosesserRegistrerteHardDeletes") {
        coEvery { repo.hardDelete(any()) } returns Unit

        context("sletter notifikasjoner som er registrert for sletting") {
            val harddeletes = listOf(
                registrertHardDelete(uuid("1"), Oppgave, "tag"),
                registrertHardDelete(uuid("2"), Oppgave, "tag"),
            )
            coEvery { repo.finnRegistrerteHardDeletes(any()) } returns harddeletes

            it("sletter aggregater") {
                service.prosesserRegistrerteHardDeletes()

                coVerify { repo.hardDelete(uuid("1")) }
                coVerify { repo.hardDelete(uuid("2")) }
            }
        }

        context("lager harddelete events for alle notifikasjoner som er tilkoblet en sak") {
            kafkaProducer.clear()
            val harddeletes = listOf(
                registrertHardDelete(uuid("1"), Sak, "tag", "42"),
                registrertHardDelete(uuid("1"), Sak, "foo", "44"),
            )
            coEvery { repo.finnRegistrerteHardDeletes(any()) } returns harddeletes
            coEvery { repo.hentNotifikasjonerForSak("tag", "42") } returns listOf(
                notifikasjonForSak(uuid("11")),
                notifikasjonForSak(uuid("12")),
            )
            coEvery { repo.hentNotifikasjonerForSak("foo", "44") } returns emptyList()

            it("sender hardDelete for aggregater som skal slettes") {
                service.prosesserRegistrerteHardDeletes()
                coVerify { repo.hardDelete(uuid("1")) }

                val hardDeletes = kafkaProducer.hendelserOfType<HendelseModel.HardDelete>()
                val deletedIds = hardDeletes.map(HendelseModel.HardDelete::aggregateId)
                val expected = listOf(uuid("11"), uuid("12"))

                deletedIds shouldContainExactlyInAnyOrder expected
            }
        }
    }
})

private fun notifikasjonForSak(aggregateId: UUID) = SkedulertHardDeleteRepository.NotifikasjonForSak(
    aggregateId = aggregateId,
    virksomhetsnummer = "21",
    produsentid = "test",
    merkelapp = "tag",
)

private fun skedulertHardDelete(
    aggregateId: UUID,
    beregnetSlettetidspunkt: Instant = Instant.EPOCH
) = SkedulertHardDeleteRepository.SkedulertHardDelete(
    aggregateId = aggregateId,
    aggregateType = Oppgave,
    virksomhetsnummer = "21",
    produsentid = "test",
    merkelapp = "tag",
    inputBase = OffsetDateTime.now(),
    inputOm = null,
    inputDen = LocalDateTime.now(),
    grupperingsid = null,
    beregnetSlettetidspunkt = beregnetSlettetidspunkt,
)


private fun registrertHardDelete(
    aggregateId: UUID,
    aggregateType: SkedulertHardDeleteRepository.AggregateType,
    merkelapp: String,
    grupperingsid: String? = null,
) = SkedulertHardDeleteRepository.RegistrertHardDelete(
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    virksomhetsnummer = "21",
    produsentid = "test",
    merkelapp = merkelapp,
    grupperingsid = grupperingsid,
)
