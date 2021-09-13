package no.nav.arbeidsgiver.notifikasjon.statistikk

import no.nav.arbeidsgiver.notifikasjon.Hendelse

interface StatistikkService {
    suspend fun håndterHendelse(hendelse: Hendelse)
}

class AbacusServiceImpl(
    private val statistikkModel: StatistikkModel
) : StatistikkService {

    override suspend fun håndterHendelse(hendelse: Hendelse) {
        statistikkModel.oppdaterModellEtterHendelse(hendelse)
    }
}
