package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent

interface KafkaReaperService {
    suspend fun håndterHendelse(hendelse: Hendelse)
}

class KafkaReaperServiceImpl(
    private val kafkaReaperModel: KafkaReaperModel,
    private val kafkaProducer: HendelseProdusent,
) : KafkaReaperService {

    override suspend fun håndterHendelse(hendelse: Hendelse) {
        kafkaReaperModel.oppdaterModellEtterHendelse(hendelse)

        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored : Unit = when (hendelse) {
            is HardDelete -> {
                for (relatertHendelseId in kafkaReaperModel.alleRelaterteHendelser(hendelse.aggregateId)) {
                    kafkaProducer.tombstone(
                        key = relatertHendelseId,
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
            is OppgaveUtgått,
            is PåminnelseOpprettet,
            is EksterntVarselFeilet,
            is EksterntVarselVellykket -> {
                if (kafkaReaperModel.erSlettet(hendelse.aggregateId)) {
                    kafkaProducer.tombstone(
                        key = hendelse.hendelseId,
                        orgnr = hendelse.virksomhetsnummer
                    )
                    kafkaReaperModel.fjernRelasjon(hendelse.hendelseId)
                } else Unit
            }
        }
    }
}
