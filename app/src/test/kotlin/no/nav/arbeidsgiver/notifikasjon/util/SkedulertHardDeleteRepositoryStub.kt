package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository
import java.time.Instant
import java.util.*

open class SkedulertHardDeleteRepositoryStub : SkedulertHardDeleteRepository {
    override suspend fun hentSkedulerteHardDeletes(tilOgMed: Instant): List<SkedulertHardDeleteRepository.SkedulertHardDelete> {
        TODO("Not yet implemented")
    }

    override suspend fun hentNotifikasjonerForSak(
        merkelapp: String,
        grupperingsid: String
    ): List<SkedulertHardDeleteRepository.NotifikasjonForSak> {
        TODO("Not yet implemented")
    }

    override suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, kafkaTimestamp: Instant) {
        TODO("Not yet implemented")
    }

    override suspend fun hardDelete(aggregateId: UUID) {
        TODO("Not yet implemented")
    }

    override suspend fun finnRegistrerteHardDeletes(limit: Int): List<SkedulertHardDeleteRepository.RegistrertHardDelete> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteOrphanedHardDeletes(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun hent(aggregateId: UUID): SkedulertHardDeleteRepository.SkedulertHardDelete? {
        TODO("Not yet implemented")
    }
}