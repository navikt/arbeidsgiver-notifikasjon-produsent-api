package no.nav.arbeidsgiver.notifikasjon.statistikk

import io.micrometer.core.instrument.MultiGauge
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics

interface StatistikkService {
    suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata)
}

class StatistikkServiceImpl(
    private val statistikkModel: StatistikkModel
) : StatistikkService {

    private val antallNotifikasjoner = MultiGauge.builder("antall_notifikasjoner")
        .description("Antall notifikasjoner")
        .register(Metrics.meterRegistry)

    private val antallSaker = MultiGauge.builder("antall_saker")
        .description("Antall saker")
        .register(Metrics.meterRegistry)

    private val antallUnikeTekster = MultiGauge.builder("antall_unike_tekster")
        .description("Unike tekster")
        .register(Metrics.meterRegistry)

    private val antallKlikk = MultiGauge.builder("antall_klikk")
        .description("Antall klikk på notifikasjon")
        .register(Metrics.meterRegistry)

    private val antallKlikketPaa = MultiGauge.builder("antall_klikket_paa")
        .description("Antall notifikasjoner som er klikket på")
        .register(Metrics.meterRegistry)

    private val antallUtførte = MultiGauge.builder("antall_utforte")
        .description("Antall utførte (med histogram)")
        .register(Metrics.meterRegistry)

    private val antallVarsler = MultiGauge.builder("antall_varsler")
        .description("Antall varsler")
        .register(Metrics.meterRegistry)

    override suspend fun håndterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        statistikkModel.oppdaterModellEtterHendelse(hendelse, metadata)
    }

    suspend fun updateGauges() {
        antallNotifikasjoner.register(statistikkModel.antallNotifikasjoner(), true)
        antallSaker.register(statistikkModel.antallSaker(), true)
        antallUnikeTekster.register(statistikkModel.antallUnikeTekster(), true)
        antallKlikk.register(statistikkModel.antallKlikk(), true)
        antallUtførte.register(statistikkModel.antallUtførteHistogram(), true)
        antallVarsler.register(statistikkModel.antallVarsler(), true)
        antallKlikketPaa.register(statistikkModel.antallKlikketPaa(), true)
    }
}
