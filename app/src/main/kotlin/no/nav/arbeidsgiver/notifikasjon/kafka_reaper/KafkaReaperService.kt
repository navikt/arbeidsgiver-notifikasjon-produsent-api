package no.nav.arbeidsgiver.notifikasjon.kafka_reaper

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselKansellert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOppdatert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NesteStegSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.TilleggsinformasjonSak
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

        when (hendelse) {
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
            is NesteStegSak,
            is TilleggsinformasjonSak,
            is NyStatusSak,
            is SoftDelete,
            is BeskjedOpprettet,
            is OppgaveOpprettet,
            is KalenderavtaleOpprettet,
            is KalenderavtaleOppdatert,
            is BrukerKlikket,
            is OppgaveUtført,
            is OppgaveUtgått,
            is PåminnelseOpprettet,
            is FristUtsatt,
            is EksterntVarselFeilet,
            is EksterntVarselKansellert,
            is EksterntVarselVellykket -> {
                if (kafkaReaperModel.erSlettet(hendelse.aggregateId)) {
                    kafkaProducer.tombstone(
                        key = hendelse.hendelseId,
                        orgnr = hendelse.virksomhetsnummer
                    )
                    kafkaReaperModel.fjernRelasjon(hendelse.hendelseId)
                } else Unit
            }

            is HendelseModel.PaaminnelseEndret -> TODO()
        }
    }
}
