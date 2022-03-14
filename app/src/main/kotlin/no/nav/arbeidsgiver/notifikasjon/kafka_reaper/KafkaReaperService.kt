package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey

interface KafkaReaperService {
    suspend fun håndterHendelse(hendelse: Hendelse)
}

class KafkaReaperServiceImpl(
    private val kafkaReaperModel: KafkaReaperModel,
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>
) : KafkaReaperService {

    override suspend fun håndterHendelse(hendelse: Hendelse) {
        kafkaReaperModel.oppdaterModellEtterHendelse(hendelse)

        val ignored : Unit = when (hendelse) {
            is HardDelete -> {
                for (relatertHendelseId in kafkaReaperModel.alleRelaterteHendelser(hendelse.aggregateId)) {
                    kafkaProducer.tombstone(
                        key = relatertHendelseId.toString(),
                        orgnr = hendelse.virksomhetsnummer
                    )
                    kafkaReaperModel.fjernRelasjon(relatertHendelseId)
                }
            }
            is SakOpprettet,
            is NyStatusSak,
            is SoftDelete,
            is BeskjedOpprettet,
            is OppgaveOpprettet,
            is BrukerKlikket,
            is OppgaveUtført,
            is EksterntVarselFeilet,
            is EksterntVarselVellykket -> {
                if (kafkaReaperModel.erSlettet(hendelse.aggregateId)) {
                    kafkaProducer.tombstone(
                        key = hendelse.hendelseId.toString(),
                        orgnr = hendelse.virksomhetsnummer
                    )
                    kafkaReaperModel.fjernRelasjon(hendelse.hendelseId)
                } else Unit
            }
        }
    }
}
