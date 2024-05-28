
package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.util.*

open class ProdusentRepositoryStub : ProdusentRepository {
    override suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon? {
        TODO("Not yet implemented")
    }

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? {
        TODO("Not yet implemented")
    }

    override suspend fun finnNotifikasjoner(
        merkelapper: List<String>,
        grupperingsid: String?,
        antall: Int,
        offset: Int
    ): List<ProdusentModel.Notifikasjon> {
        TODO("Not yet implemented")
    }

    override suspend fun hentSak(grupperingsid: String, merkelapp: String): ProdusentModel.Sak? {
        TODO("Not yet implemented")
    }

    override suspend fun hentSak(id: UUID): ProdusentModel.Sak? {
        TODO("Not yet implemented")
    }

    override suspend fun erHardDeleted(
        type: ProdusentRepository.AggregateType,
        merkelapp: String,
        grupperingsid: String
    ): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        TODO("Not yet implemented")
    }

    override suspend fun notifikasjonOppdateringFinnes(id: UUID, idempotenceKey: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun nesteStegSakFinnes(id: UUID, idempotenceKey: String): Boolean {
        TODO("Not yet implemented")
    }
}