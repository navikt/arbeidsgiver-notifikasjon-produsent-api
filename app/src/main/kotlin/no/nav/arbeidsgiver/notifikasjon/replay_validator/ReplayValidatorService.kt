package no.nav.arbeidsgiver.notifikasjon.replay_validator

import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionHendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionProcessor
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.EphemeralDatabase
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.executeQuery
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.executeUpdate
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.sql.ResultSet


class ReplayValidatorService : PartitionProcessor {
    private val log = logger()
    private val antallOpprettelserEtterHardDelete = MultiGauge.builder("antall_notifikasjoner_opprettet_etter_delete")
        .description("Antall notifikasjoner opprettet etter hard delete av sak")
        .register(Metrics.meterRegistry)

    internal val repository = ReplayValidatorRepository()

    override fun close() = repository.close()

    override suspend fun processHendelse(hendelse: HendelseModel.Hendelse, metadata: PartitionHendelseMetadata) {
        when (hendelse) {
            is HendelseModel.OppgaveOpprettet,
            is HendelseModel.BeskjedOpprettet,
            -> {
                repository.insertNotifikasjonCreate(
                    id = hendelse.aggregateId.toString(),
                    produsentId = hendelse.produsentId ?: "ukjent produsent",
                    merkelapp = when (hendelse) {
                        is HendelseModel.OppgaveOpprettet -> hendelse.merkelapp
                        is HendelseModel.BeskjedOpprettet -> hendelse.merkelapp
                        else -> throw IllegalStateException("Uventet hendelse $hendelse")
                    },
                    grupperingsid = when (hendelse) {
                        is HendelseModel.OppgaveOpprettet -> hendelse.grupperingsid
                        is HendelseModel.BeskjedOpprettet -> hendelse.grupperingsid
                        else -> throw IllegalStateException("Uventet hendelse $hendelse")
                    },
                    createdOffset = metadata.offset.toString(),
                    createdPartition = metadata.partition.toString(),
                )
            }

            is HendelseModel.HardDelete -> {
                if (hendelse.erSak) {
                    repository.insertSakHardDelete(
                        id = hendelse.aggregateId.toString(),
                        produsentId = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp!!,
                        grupperingsid = hendelse.grupperingsid!!,
                        deletedOffset = metadata.offset.toString(),
                        deletedPartition = metadata.partition.toString(),
                    )
                }
            }

            is HendelseModel.SakOpprettet,
            is HendelseModel.OppgaveUtført,
            is HendelseModel.OppgaveUtgått,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.FristUtsatt,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.SoftDelete,
            is HendelseModel.NyStatusSak -> Unit
        }

    }

    override suspend fun processingLoopStep() {
        repository.findNotifikasjonCreatesAfterHardDeleteSak().forEach {
            log.warn("NotifikasjonCreateAfterHardDeleteSak: partition={} offset={}", it.createdPartition, it.createdOffset)
        }

        antallOpprettelserEtterHardDelete.register(
            repository.findNotifikasjonCreatesAfterHardDeleteSak()
                .groupBy { it.produsentId }
                .map { (key, value) ->
                    MultiGauge.Row.of(
                        Tags.of("produsent_id", key),
                        value.size.toDouble()
                    )
                },
            true
        )
    }
}

class ReplayValidatorRepository : AutoCloseable {
    private val database = EphemeralDatabase(
        "replay_validator",
        """
        create table notifikasjon_creates (
            id text not null primary key,
            produsent_id text not null,
            merkelapp text not null,
            grupperingsid text,
            created_offset bigint,
            created_partition int
        );
        
        create table sak_hard_deletes (
            id text not null primary key,
            produsent_id text not null,
            merkelapp text not null,
            grupperingsid text not null,
            deleted_offset bigint,
            deleted_partition int
        );
        """.trimIndent()
    )

    override fun close() = database.close()

    fun insertNotifikasjonCreate(
        id: String,
        produsentId: String,
        merkelapp: String,
        grupperingsid: String?,
        createdOffset: String,
        createdPartition: String,
    ) {
        database.useTransaction {
            executeUpdate(
                """
                insert into notifikasjon_creates (id, produsent_id, merkelapp, grupperingsid, created_offset, created_partition)
                values (?, ?, ?, ?, ?, ?)
                on conflict (id) do nothing
                """.trimIndent(),
                setup = {
                    setText(id)
                    setText(produsentId)
                    setText(merkelapp)
                    setTextOrNull(grupperingsid)
                    setText(createdOffset)
                    setText(createdPartition)
                }
            )
        }
    }

    fun insertSakHardDelete(
        id: String,
        produsentId: String,
        merkelapp: String,
        grupperingsid: String,
        deletedOffset: String,
        deletedPartition: String,
    ) {
        database.useTransaction {
            executeUpdate(
                """
                insert into sak_hard_deletes (id, produsent_id, merkelapp, grupperingsid, deleted_offset, deleted_partition)
                values (?, ?, ?, ?, ?, ?)
                on conflict (id) do nothing
                """.trimIndent(),
                setup = {
                    setText(id)
                    setText(produsentId)
                    setText(merkelapp)
                    setText(grupperingsid)
                    setText(deletedOffset)
                    setText(deletedPartition)
                }
            )
        }
    }

    fun findNotifikasjonCreatesAfterHardDeleteSak(): List<NotifikasjonCreateAfterHardDeleteSak> {
        return database.useTransaction {
            executeQuery(
                """
                select * from notifikasjon_creates n 
                where exists (
                    select s.id from sak_hard_deletes s 
                    where s.merkelapp = n.merkelapp
                        and s.grupperingsid = n.grupperingsid
                        and s.deleted_offset is not null
                        and s.deleted_offset < n.created_offset
                )
                """.trimIndent(),
                setup = {},
                result = {
                    val results = mutableListOf<NotifikasjonCreateAfterHardDeleteSak>()
                    while (next()) {
                        results.add(asNotifikasjonCreateAfterHardDeleteSak())
                    }
                    results
                }
            )
        }
    }
}

data class NotifikasjonCreateAfterHardDeleteSak(
    val id: String,
    val produsentId: String,
    val merkelapp: String,
    val grupperingsid: String,
    val createdOffset: String,
    val createdPartition: String,
)

private fun ResultSet.asNotifikasjonCreateAfterHardDeleteSak() =
    NotifikasjonCreateAfterHardDeleteSak(
        id = getString("id"),
        produsentId = getString("produsent_id"),
        merkelapp = getString("merkelapp"),
        grupperingsid = getString("grupperingsid"),
        createdOffset = getString("created_offset"),
        createdPartition = getString("created_partition"),
    )
