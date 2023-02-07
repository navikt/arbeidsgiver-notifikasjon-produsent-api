package no.nav.arbeidsgiver.notifikasjon.dataprodukt

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata

interface DataproduktService {
    suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata)
}

class DataproduktServiceImpl(
    private val dataproduktModel: DataproduktModel
) : DataproduktService {
    override suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        dataproduktModel.oppdaterModellEtterHendelse(hendelse, metadata)
    }
}
