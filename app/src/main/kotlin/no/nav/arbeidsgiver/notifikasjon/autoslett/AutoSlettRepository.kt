package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class AutoSlettRepository(
    private val database: Database
) {
    suspend fun saveAggregate(
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

    suspend fun hardDelete(aggregateId: UUID) {
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
        }.firstOrNull()
    }

    suspend fun upsert(
        aggregateId: UUID,
        scheduledTime: ScheduledTime,
    ) {
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
            timestamp_utc(scheduledTime.happensAt())
            timestamp_utc(scheduledTime.baseTime)
            nullableString(scheduledTime.omOrNull()?.toString())
            nullableString(scheduledTime.denOrNull()?.toString())
        }
    }
}

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
)