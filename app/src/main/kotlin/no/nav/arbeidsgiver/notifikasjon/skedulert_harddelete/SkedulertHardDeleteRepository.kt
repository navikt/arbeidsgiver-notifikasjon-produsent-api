package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyTidStrategi.FORLENG
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class SkedulertHardDeleteRepository(
    private val database: Database
) {
    data class SkedulertHardDelete(
        val aggregateId: UUID,
        val aggregateType: String,
        val virksomhetsnummer: String,
        val produsentid: String,
        val merkelapp: String,
        val inputBase: OffsetDateTime,
        val inputOm: ISO8601Period?,
        val inputDen: LocalDateTime?,
        val beregnetSlettetidspunkt: Instant,
    ) {
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

    suspend fun hentDeSomSkalSlettes(
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

    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, timestamp: Instant) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored = when (hendelse) {
            is HendelseModel.BeskjedOpprettet -> {
                saveAggregate(hendelse, "Beskjed", hendelse.merkelapp)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                )
            }

            is HendelseModel.OppgaveOpprettet -> {
                saveAggregate(hendelse, "Oppgave", hendelse.merkelapp)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                )
            }

            is HendelseModel.SakOpprettet -> {
                saveAggregate(hendelse, "Sak", hendelse.merkelapp)
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                )
            }

            is HendelseModel.OppgaveUtført -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = timestamp.atOffset(ZoneOffset.UTC),
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.OppgaveUtgått -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    hardDelete = hendelse.hardDelete,
                    opprettetTidspunkt = timestamp.atOffset(ZoneOffset.UTC),
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.NyStatusSak -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    opprettetTidspunkt = hendelse.opprettetTidspunkt,
                    hardDelete = hendelse.hardDelete,
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.HardDelete -> hardDelete(hendelse.aggregateId)
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.SoftDelete -> Unit
        }
    }


    private suspend fun saveAggregate(
        hendelse: HendelseModel.Hendelse,
        aggregateType: String,
        merkelapp: String,
    ) {
        database.nonTransactionalExecuteUpdate(
            """
                insert into aggregate (
                    aggregate_id, 
                    aggregate_type, 
                    virksomhetsnummer, 
                    produsentid, 
                    merkelapp
                ) values (?, ?, ?, ?, ?) on conflict do nothing
                """
        ) {
            uuid(hendelse.aggregateId)
            string(aggregateType)
            string(hendelse.virksomhetsnummer)
            string(hendelse.produsentId ?: "ukjent")
            string(merkelapp)
        }
    }

    private suspend fun hardDelete(aggregateId: UUID) {
        database.nonTransactionalExecuteUpdate("""
           delete from aggregate where aggregate_id = ? 
        """) {
            uuid(aggregateId)
        }
    }

    suspend fun hent(aggregateId: UUID): SkedulertHardDelete? {
        return database.nonTransactionalExecuteQuery("""
            select 
                aggregate.aggregate_id,
                aggregate.aggregate_type,
                aggregate.virksomhetsnummer,
                aggregate.produsentid,
                aggregate.merkelapp,
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
            aggregateId = getObject("aggregate_id", UUID::class.java),
            aggregateType = getString("aggregate_type"),
            virksomhetsnummer = getString("virksomhetsnummer"),
            produsentid = getString("produsentid"),
            merkelapp = getString("merkelapp"),
            inputBase = getObject("input_base", OffsetDateTime::class.java),
            inputOm = getString("input_om")?.let { ISO8601Period.parse(it) },
            inputDen = getString("input_den")?.let { LocalDateTime.parse(it) },
            beregnetSlettetidspunkt = getObject("beregnet_slettetidspunkt", OffsetDateTime::class.java).toInstant(),
        )

    private suspend fun upsert(
        aggregateId: UUID,
        hardDelete: HendelseModel.HardDeleteUpdate?,
        opprettetTidspunkt: OffsetDateTime,
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
        opprettetTidspunkt: OffsetDateTime,
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
            nullableString(scheduledTime.omOrNull()?.toString())
            nullableString(scheduledTime.denOrNull()?.toString())
        }
    }
}
