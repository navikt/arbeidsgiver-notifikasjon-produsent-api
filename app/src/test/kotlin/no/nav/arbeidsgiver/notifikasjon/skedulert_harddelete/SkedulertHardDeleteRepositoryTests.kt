package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant.now
import java.time.OffsetDateTime
import java.util.*

class SkedulertHardDeleteRepositoryTests : DescribeSpec({
    val database = testDatabase(SkedulertHardDelete.databaseConfig)
    val repository = SkedulertHardDeleteRepository(database)

    describe("SkedulertHardDeleteRepository#cleanupOrphanedHardDeletes") {
        repository.oppdaterModellEtterHendelse(beskjedOpprettet("42"), now())
        repository.oppdaterModellEtterHendelse(hardDelete("42"), now())
        repository.oppdaterModellEtterHendelse(hardDelete("314"), now()) // orphan

        it("rydder opp i harddelete-tabellen") {
            repository.deleteOrphanedHardDeletes()

            repository.finnRegistrerteHardDeletes(100).map { it.aggregateId }.let {
                it shouldBe listOf(uuid("42"))
            }
        }
    }
})

private fun hardDelete(idsuffix: String) = HendelseModel.HardDelete(
    virksomhetsnummer = idsuffix,
    aggregateId = uuid(idsuffix),
    hendelseId = UUID.randomUUID(),
    produsentId = idsuffix,
    kildeAppNavn = "test-app",
    deletedAt = OffsetDateTime.now(),
    grupperingsid = null,
)

private fun beskjedOpprettet(idsuffix: String) =
    EksempelHendelse.BeskjedOpprettet.copy(
        virksomhetsnummer = idsuffix, notifikasjonId = uuid(
            idsuffix
        )
    )
