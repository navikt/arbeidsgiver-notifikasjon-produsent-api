package no.nav.arbeidsgiver.notifikasjon.statistikk

import io.micrometer.core.instrument.MultiGauge
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health

interface StatistikkService {
    suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata)
}

class AbacusServiceImpl(
    private val statistikkModel: StatistikkModel
) : StatistikkService {

    private val antallNotifikasjoner = MultiGauge.builder("antall_notifikasjoner")
        .description("Antall notifikasjoner")
        .register(Health.meterRegistry)

    private val antallUnikeTekster = MultiGauge.builder("antall_unike_tekster")
        .description("Unike tekster")
        .register(Health.meterRegistry)

    private val antallKlikk = MultiGauge.builder("antall_klikk")
        .description("Antall klikk på notifikasjon")
        .register(Health.meterRegistry)

    private val antallUtførte = MultiGauge.builder("antall_utforte")
        .description("Antall utførte (med histogram)")
        .register(Health.meterRegistry)

    override suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        statistikkModel.oppdaterModellEtterHendelse(hendelse, metadata)
    }

    suspend fun updateGauges() {
        antallNotifikasjoner.register(statistikkModel.antallNotifikasjoner(), true)
        antallUnikeTekster.register(statistikkModel.antallUnikeTekster(), true)
        antallKlikk.register(statistikkModel.antallKlikk(), true)
        antallUtførte.register(statistikkModel.antallUtførteHistogram(), true)
    }
}
