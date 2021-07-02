package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database

interface KafkaReaperModel {
    fun oppdaterModellEtterHendelse(hendelse: Hendelse)
}

class KafkaReaperModelImpl(
    database: Database
) : KafkaReaperModel {
    override fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        val ignore: Unit = when (hendelse) {
            is Hendelse.SoftDelete -> Unit
            is Hendelse.HardDelete -> Unit
            is Hendelse.OppgaveUtført -> Unit
            is Hendelse.BrukerKlikket -> Unit
            is Hendelse.BeskjedOpprettet -> Unit
            is Hendelse.OppgaveOpprettet -> Unit
        }
    }
}