package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem.AUTOSLETT_SERVICE
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.AggregateType
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.SkedulertHardDelete
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.SkedulertHardDeleteRepositoryStub
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class SkedulertHardDeleteServiceTest : DescribeSpec({

    val kafkaProducer = FakeHendelseProdusent()
    val repo = object : SkedulertHardDeleteRepositoryStub() {
        val hentSkedulerteHardDeletes = mutableListOf<List<SkedulertHardDelete>>()
        override suspend fun hentSkedulerteHardDeletes(tilOgMed: Instant) = hentSkedulerteHardDeletes.removeLast()
    }
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
            repo.hentSkedulerteHardDeletes.add(skalSlettes)

            service.sendSkedulerteHardDeletes(nåTidspunkt)

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
            repo.hentSkedulerteHardDeletes.add(skalSlettes)

            it("validering feiler og metoden kaster") {
                service.sendSkedulerteHardDeletes(nåTidspunkt)

                Health.subsystemAlive[AUTOSLETT_SERVICE] shouldBe false
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
    beregnetSlettetidspunkt: Instant = Instant.EPOCH,
    aggregateType: AggregateType = AggregateType.Oppgave,
    grupperingsid: String? = null,
) = SkedulertHardDelete(
    aggregateId = aggregateId,
    aggregateType = aggregateType,
    virksomhetsnummer = "21",
    produsentid = "test",
    merkelapp = "tag",
    inputBase = OffsetDateTime.now(),
    inputOm = null,
    inputDen = LocalDateTime.now(),
    grupperingsid = grupperingsid,
    beregnetSlettetidspunkt = beregnetSlettetidspunkt,
)


private fun registrertHardDelete(
    aggregateId: UUID,
    aggregateType: AggregateType,
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
