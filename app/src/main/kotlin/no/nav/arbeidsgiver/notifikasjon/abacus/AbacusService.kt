package no.nav.arbeidsgiver.notifikasjon.abacus

import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import java.security.MessageDigest

interface AbacusService {
    suspend fun håndterHendelse(hendelse: Hendelse)
}

class AbacusServiceImpl(
    private val abacusModel: AbacusModel
) : AbacusService {

    override suspend fun håndterHendelse(hendelse: Hendelse) {
        abacusModel.oppdaterModellEtterHendelse(hendelse)
    }
}
