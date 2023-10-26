package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyTidStrategi.FORLENG
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.AggregateType.*
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class SkedulertHardDeleteRepository(
    private val database: Database
) {
    data class SkedulertHardDelete(
        val aggregateId: UUID,
        val aggregateType: AggregateType,
        val virksomhetsnummer: String,
        val produsentid: String,
        val merkelapp: String,
        val grupperingsid: String?,
        val inputBase: OffsetDateTime,
        val inputOm: ISO8601Period?,
        val inputDen: LocalDateTime?,
        val beregnetSlettetidspunkt: Instant,
    ) {
        val isSak = aggregateType == Sak

        fun loggableToString() = mapOf(
            "aggregateId" to aggregateId,
            "aggregateType" to aggregateType,
            "produsentid" to produsentid,
            "inputBase" to inputBase,
            "inputOm" to inputOm,
            "inputDen" to inputDen,
            "beregnetSlettetidspunkt" to beregnetSlettetidspunkt,
        ).toString()
    }

    data class RegistrertHardDelete(
        val aggregateId: UUID,
        val aggregateType: AggregateType,
        val virksomhetsnummer: String,
        val produsentid: String,
        val merkelapp: String,
        val grupperingsid: String?,
    ) {
        val isSak = aggregateType == Sak

        fun loggableToString() = mapOf(
            "aggregateId" to aggregateId,
            "aggregateType" to aggregateType,
            "produsentid" to produsentid,
        ).toString()
    }

    enum class AggregateType {
        Beskjed,
        Oppgave,
        Sak,
    }

    data class NotifikasjonForSak(
        val aggregateId: UUID,
        val virksomhetsnummer: String,
        val produsentid: String,
        val merkelapp: String,
    )

    suspend fun hentSkedulerteHardDeletes(
        tilOgMed: Instant,
    ): List<SkedulertHardDelete> {
        return database.nonTransactionalExecuteQuery(
            sql = """
            select 
                aggregate.aggregate_id,
                aggregate.aggregate_type,
                aggregate.virksomhetsnummer,
                aggregate.produsentid,
                aggregate.merkelapp,
                aggregate.grupperingsid,
                skedulert_hard_delete.beregnet_slettetidspunkt,
                skedulert_hard_delete.input_base,
                skedulert_hard_delete.input_om,
                skedulert_hard_delete.input_den 
            from skedulert_hard_delete 
            join aggregate on aggregate.aggregate_id = skedulert_hard_delete.aggregate_id
            where beregnet_slettetidspunkt <= ?
            order by beregnet_slettetidspunkt
            limit 100
        """,
            setup = {
                timestamp_without_timezone_utc(tilOgMed)
            },
            transform = {
                this.toSkedulertHardDelete()
            }
        )
    }

    suspend fun hentNotifikasjonerForSak(
        merkelapp: String,
        grupperingsid: String,
    ): List<NotifikasjonForSak> {
        return database.nonTransactionalExecuteQuery(
            sql = """
            select 
                aggregate.aggregate_id,
                aggregate.aggregate_type,
                aggregate.virksomhetsnummer,
                aggregate.produsentid,
                aggregate.merkelapp,
                aggregate.grupperingsid
            from aggregate 
            where merkelapp = ? and grupperingsid = ?
        """,
            setup = {
                text(merkelapp)
                text(grupperingsid)
            },
            transform = {
                NotifikasjonForSak(
                    aggregateId = getObject("aggregate_id", UUID::class.java),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    produsentid = getString("produsentid"),
                    merkelapp = getString("merkelapp")
                )
            }
        )
    }

    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, kafkaTimestamp: Instant) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored = when (hendelse) {
            is HendelseModel.BeskjedOpprettet -> {
                saveAggregate(hendelse, Beskjed, hendelse.merkelapp, hendelse.grupperingsid)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                )
            }

            is HendelseModel.OppgaveOpprettet -> {
                saveAggregate(hendelse, Oppgave, hendelse.merkelapp, hendelse.grupperingsid)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                )
            }

            is HendelseModel.SakOpprettet -> {
                saveAggregate(hendelse, Sak, hendelse.merkelapp, hendelse.grupperingsid)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt(kafkaTimestamp),
                )
            }

            is HendelseModel.OppgaveUtført -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = kafkaTimestamp,
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.OppgaveUtgått -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = kafkaTimestamp,
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.NyStatusSak -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    hardDelete = hendelse.hardDelete,
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.HardDelete -> registrerHardDelete(hendelse)
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.FristUtsatt,
            is HendelseModel.SoftDelete -> Unit
        }
    }


    private suspend fun saveAggregate(
        hendelse: HendelseModel.Hendelse,
        aggregateType: AggregateType,
        merkelapp: String,
        grupperingsid: String?,
    ) {
        database.nonTransactionalExecuteUpdate(
            """
                insert into aggregate (
                    aggregate_id, 
                    aggregate_type, 
                    virksomhetsnummer, 
                    produsentid, 
                    merkelapp,
                    grupperingsid
                ) values (?, ?, ?, ?, ?, ?) on conflict (aggregate_id) do 
                update set 
                    aggregate_type = EXCLUDED.aggregate_type,
                    virksomhetsnummer = EXCLUDED.virksomhetsnummer,
                    produsentid = EXCLUDED.produsentid,
                    merkelapp = EXCLUDED.merkelapp,
                    grupperingsid = EXCLUDED.grupperingsid
                """
        ) {
            uuid(hendelse.aggregateId)
            text(aggregateType.name)
            text(hendelse.virksomhetsnummer)
            text(hendelse.produsentId ?: "ukjent")
            text(merkelapp)
            nullableText(grupperingsid)
        }
    }

    private suspend fun registrerHardDelete(hardDelete: HendelseModel.HardDelete) {
        database.nonTransactionalExecuteUpdate("""
           insert into registrert_hard_delete_event (
                aggregate_id, hendelse_id, deleted_at 
           ) values (?, ?, ?) on conflict do nothing 
        """) {
            uuid(hardDelete.aggregateId)
            uuid(hardDelete.hendelseId)
            offsetDateTimeAsText(hardDelete.deletedAt)
        }
    }

    suspend fun hardDelete(aggregateId: UUID) {
        database.transaction {
            executeUpdate("""
                delete from aggregate where aggregate_id = ?  
            """) {
                uuid(aggregateId)
            }
            executeUpdate("""
                delete from registrert_hard_delete_event where aggregate_id = ?  
            """) {
                uuid(aggregateId)
            }
        }
    }

    suspend fun finnRegistrerteHardDeletes(limit: Int): List<RegistrertHardDelete> {
        return database.nonTransactionalExecuteQuery("""
            select 
                aggregate.aggregate_id,
                aggregate.aggregate_type,
                aggregate.virksomhetsnummer,
                aggregate.produsentid,
                aggregate.merkelapp,
                aggregate.grupperingsid 
            from registrert_hard_delete_event
            join aggregate on aggregate.aggregate_id = registrert_hard_delete_event.aggregate_id
            order by deleted_at
            limit ?
        """, {
            integer(limit)
        }) {
            RegistrertHardDelete(
                aggregateId = getObject("aggregate_id", UUID::class.java),
                aggregateType = valueOf(getString("aggregate_type")),
                virksomhetsnummer = getString("virksomhetsnummer"),
                produsentid = getString("produsentid"),
                merkelapp = getString("merkelapp"),
                grupperingsid = getString("grupperingsid"),
            )
        }
    }

    suspend fun deleteOrphanedHardDeletes() = database.nonTransactionalExecuteUpdate("""
        delete from registrert_hard_delete_event 
            where aggregate_id not in (select aggregate_id from aggregate);
    """)

    suspend fun hent(aggregateId: UUID): SkedulertHardDelete? {
        return database.nonTransactionalExecuteQuery("""
            select 
                aggregate.aggregate_id,
                aggregate.aggregate_type,
                aggregate.virksomhetsnummer,
                aggregate.produsentid,
                aggregate.merkelapp,
                aggregate.grupperingsid,
                skedulert_hard_delete.beregnet_slettetidspunkt,
                skedulert_hard_delete.input_base,
                skedulert_hard_delete.input_om,
                skedulert_hard_delete.input_den 
            from skedulert_hard_delete 
            join aggregate on aggregate.aggregate_id = skedulert_hard_delete.aggregate_id
            where skedulert_hard_delete.aggregate_id = ?
        """, {
            uuid(aggregateId)
        }) {
            this.toSkedulertHardDelete()
        }.firstOrNull()
    }

    private fun ResultSet.toSkedulertHardDelete() =
        SkedulertHardDelete(
            aggregateId = getObject("aggregate_id", java.util.UUID::class.java),
            aggregateType = valueOf(getString("aggregate_type")),
            virksomhetsnummer = getString("virksomhetsnummer"),
            produsentid = getString("produsentid"),
            merkelapp = getString("merkelapp"),
            grupperingsid = getString("grupperingsid"),
            inputBase = getObject("input_base", java.time.OffsetDateTime::class.java),
            inputOm = getString("input_om")?.let { no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period.parse(it) },
            inputDen = getString("input_den")?.let { java.time.LocalDateTime.parse(it) },
            beregnetSlettetidspunkt = getObject("beregnet_slettetidspunkt", java.time.OffsetDateTime::class.java).toInstant(),
        )

    private suspend fun upsert(
        aggregateId: UUID,
        hardDelete: HendelseModel.HardDeleteUpdate?,
        opprettetTidspunkt: Instant,
        eksisterende: SkedulertHardDelete?,
    ) {
        if (hardDelete == null) return
        val scheduledTime = ScheduledTime(hardDelete.nyTid, opprettetTidspunkt)
        if (
            eksisterende != null &&
            hardDelete.strategi == FORLENG &&
            scheduledTime.happensAt().isBefore(eksisterende.beregnetSlettetidspunkt)
        ) {
            return
        }
        upsert(aggregateId, hardDelete.nyTid, opprettetTidspunkt)
    }

    private suspend fun upsert(
        aggregateId: UUID,
        hardDelete: HendelseModel.LocalDateTimeOrDuration?,
        opprettetTidspunkt: Instant,
    ) {
        if (hardDelete == null) return
        val scheduledTime = ScheduledTime(hardDelete, opprettetTidspunkt)
        database.nonTransactionalExecuteUpdate(
            """
            insert into skedulert_hard_delete (
                aggregate_id, 
                beregnet_slettetidspunkt, 
                input_base, 
                input_om, 
                input_den           
            ) values (?, ?, ?, ?, ?) 
                on conflict (aggregate_id) do 
                update set 
                    beregnet_slettetidspunkt = EXCLUDED.beregnet_slettetidspunkt,
                    input_base = EXCLUDED.input_base,
                    input_om = EXCLUDED.input_om,
                    input_den = EXCLUDED.input_den;
            """
        ) {
            uuid(aggregateId)
            timestamp_without_timezone_utc(scheduledTime.happensAt())
            timestamp_without_timezone_utc(scheduledTime.baseTime)
            nullableText(scheduledTime.omOrNull()?.toString())
            nullableText(scheduledTime.denOrNull()?.toString())
        }
    }
}
