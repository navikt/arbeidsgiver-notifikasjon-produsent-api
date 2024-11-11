
package no.nav.arbeidsgiver.notifikasjon.util

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.util.*

open class ProdusentRepositoryStub : ProdusentRepository {
    private val log = logger()
    override suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon? {
        log.warn("STUB hentNotifikasjon($id)")
        return null
    }

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? {
        log.warn("STUB hentNotifikasjon($eksternId, $merkelapp)")
        return null
    }

    override suspend fun finnNotifikasjoner(
        merkelapper: List<String>,
        grupperingsid: String?,
        antall: Int,
        offset: Int
    ): List<ProdusentModel.Notifikasjon> {
        log.warn("STUB finnNotifikasjoner($merkelapper, $grupperingsid, $antall, $offset)")
        return emptyList()
    }

    override suspend fun hentSak(grupperingsid: String, merkelapp: String): ProdusentModel.Sak? {
        log.warn("STUB hentSak($grupperingsid, $merkelapp)")
        return null
    }

    override suspend fun hentSak(id: UUID): ProdusentModel.Sak? {
        log.warn("STUB hentSak($id)")
        return null
    }

    override suspend fun erHardDeleted(
        type: ProdusentRepository.AggregateType,
        merkelapp: String,
        grupperingsid: String
    ): Boolean {
        log.warn("STUB erHardDeleted($type, $merkelapp, $grupperingsid)")
        return false
    }

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        log.warn("STUB oppdaterModellEtterHendelse($hendelse, $metadata)")
    }

    override suspend fun notifikasjonOppdateringFinnes(id: UUID, idempotenceKey: String): Boolean {
        log.warn("STUB notifikasjonOppdateringFinnes($id, $idempotenceKey)")
        return false
    }

    override suspend fun sakOppdateringFinnes(id: UUID, idempotenceKey: String): Boolean {
        log.warn("STUB sakOppdateringFinnes($id, $idempotenceKey)")
        return false
    }
}