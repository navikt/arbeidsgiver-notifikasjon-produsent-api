package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import kotlinx.coroutines.test.runTest
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem.AUTOSLETT_SERVICE
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.AggregateType
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.SkedulertHardDelete
import no.nav.arbeidsgiver.notifikasjon.util.FakeHendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.util.SkedulertHardDeleteRepositoryStub
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SkedulertHardDeleteServiceTest {

    private val kafkaProducer = FakeHendelseProdusent()
    private val repo = object : SkedulertHardDeleteRepositoryStub() {
        val hentSkedulerteHardDeletes = mutableListOf<List<SkedulertHardDelete>>()
        override suspend fun hentSkedulerteHardDeletes(tilOgMed: Instant) = hentSkedulerteHardDeletes.removeLast()
    }
    private val service = SkedulertHardDeleteService(repo, kafkaProducer)
    private val nåTidspunkt = Instant.parse("2020-01-01T20:20:01.01Z")

    @AfterEach
    fun tearDown() {
        Health.subsystemAlive[AUTOSLETT_SERVICE] = true
    }

    @Test
    fun `når de som skal slettes er gyldig`() = runTest {
        val skalSlettes = listOf(
            skedulertHardDelete(uuid("1")),
            skedulertHardDelete(uuid("2")),
        )
        repo.hentSkedulerteHardDeletes.add(skalSlettes)

        service.sendSkedulerteHardDeletes(nåTidspunkt)

        // sender hardDelete for aggregater som skal slettes
        val hardDeletes = kafkaProducer.hendelserOfType<HendelseModel.HardDelete>()
        val deletedIds = hardDeletes.map(HendelseModel.HardDelete::aggregateId)
        val expected = listOf(uuid("1"), uuid("2"))

        assertEquals(expected, deletedIds)
    }

    @Test
    fun `når de som skal slettes inneholder noe som skal slettes i fremtiden`() = runTest {
        val skalSlettes = listOf(
            skedulertHardDelete(uuid("1"), nåTidspunkt - Duration.ofSeconds(1)),
            skedulertHardDelete(uuid("2"), nåTidspunkt + Duration.ofSeconds(1)),
        )
        repo.hentSkedulerteHardDeletes.add(skalSlettes)

        // validering feiler og metoden kaster
        service.sendSkedulerteHardDeletes(nåTidspunkt)

        assertEquals(false, Health.subsystemAlive[AUTOSLETT_SERVICE])
    }
}

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
