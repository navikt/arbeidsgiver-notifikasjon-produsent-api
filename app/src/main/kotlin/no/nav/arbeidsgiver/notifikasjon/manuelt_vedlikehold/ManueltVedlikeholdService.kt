package no.nav.arbeidsgiver.notifikasjon.manuelt_vedlikehold

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionProcessor
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ManueltVedlikeholdService(
    private val hendelseProdusent: HendelseProdusent,
    private val kildeAppNavn: String,
) : PartitionProcessor {
    private val log = logger()
    private val aggregatesSeen = ConcurrentHashMap<UUID, String>()
    private val aggregatesDeleted = ConcurrentHashMap<UUID, Unit>()
    @Volatile private var stopProcessing = false

    private val aggregatesToDelete = basedOnEnv<List<String>>(
        prod = { listOf(
            "ca3f5390-822c-4bbf-b652-f1bc6154d520",
            "2abb439b-d042-4bf7-a242-02283c2b2f43",
            "19968fa0-880b-4952-b284-a636282c9734",
            "dc04043c-c3aa-4729-bfc7-84559229e2ed",
        ) },
        dev = { listOf(
            "6351df92-333d-4211-bac4-a8749aed4195",
            "166e5455-2dcb-4e67-ace1-e611b253c1ed", /* allerede slettet når kode kjører */
        )},
        other = { listOf(

        ) },
    ).map { UUID.fromString(it)!!}

    override suspend fun processHendelse(hendelse: HendelseModel.Hendelse, metadata: PartitionHendelseMetadata) {
        when (hendelse) {
            is HardDelete ->
                aggregatesDeleted[hendelse.aggregateId] = Unit

            is HendelseModel.SakOpprettet,
            is HendelseModel.KalenderavtaleOpprettet,
            is HendelseModel.OppgaveOpprettet,
            is HendelseModel.BeskjedOpprettet ->
                aggregatesSeen[hendelse.aggregateId] = hendelse.virksomhetsnummer

            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselKansellert,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.NyStatusSak,
            is HendelseModel.OppgaveUtført,
            is HendelseModel.OppgaveUtgått,
            is HendelseModel.KalenderavtaleOppdatert,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.FristUtsatt,
            is HendelseModel.NesteStegSak,
            is HendelseModel.TilleggsinformasjonSak,
            is HendelseModel.OppgavePåminnelseEndret,
            is HendelseModel.SoftDelete -> Unit

        }
    }

    override suspend fun processingLoopStep() {
        if (stopProcessing) return

        for (aggregateId in aggregatesToDelete) {
            val virksomhetsnummer = aggregatesSeen[aggregateId]

            if (virksomhetsnummer == null) {
                log.info("skipping HardDelete for aggregate id {}. aggregate not seen.", aggregateId)
                continue
            }

            if (aggregatesDeleted.containsKey(aggregateId)) {
                log.info("skipping HardDelete for aggregateid {}. already deleted.", aggregateId)
                continue
            }

            val hardDelete = HardDelete(
                virksomhetsnummer = virksomhetsnummer,
                aggregateId = aggregateId,
                hendelseId = UUID.randomUUID(),
                produsentId = "fager-manuelt-vedlikehold",
                kildeAppNavn = kildeAppNavn,
                deletedAt = OffsetDateTime.now(ZoneOffset.UTC),
                grupperingsid = null,
                merkelapp = null,
            )
            log.info("sending HardDelete for aggregateid {}", aggregateId)
            hendelseProdusent.send(hardDelete)
        }
        stopProcessing = true
    }

    override fun close() {
    }
}