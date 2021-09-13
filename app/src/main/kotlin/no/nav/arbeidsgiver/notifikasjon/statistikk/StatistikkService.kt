package no.nav.arbeidsgiver.notifikasjon.statistikk

import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseMetadata

interface StatistikkService {
    suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata)
}

class AbacusServiceImpl(
    private val statistikkModel: StatistikkModel
) : StatistikkService {

    override suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        statistikkModel.oppdaterModellEtterHendelse(hendelse, metadata)
    }
}
