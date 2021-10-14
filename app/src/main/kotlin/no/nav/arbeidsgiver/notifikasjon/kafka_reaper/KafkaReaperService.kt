package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.Hendelse
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
            is Hendelse.HardDelete -> {
                for (relatertHendelseId in kafkaReaperModel.alleRelaterteHendelser(hendelse.notifikasjonId)) {
                    kafkaProducer.tombstone(
                        key = relatertHendelseId.toString(),
                        orgnr = hendelse.virksomhetsnummer
                    )
                    kafkaReaperModel.fjernRelasjon(relatertHendelseId)
                }
            }

            is Hendelse.SoftDelete,
            is Hendelse.BeskjedOpprettet,
            is Hendelse.OppgaveOpprettet,
            is Hendelse.BrukerKlikket,
            is Hendelse.OppgaveUtført,
            is Hendelse.EksterntVarselFeilet,
            is Hendelse.EksterntVarselVellykket -> {
                if (kafkaReaperModel.erSlettet(hendelse.notifikasjonId)) {
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
