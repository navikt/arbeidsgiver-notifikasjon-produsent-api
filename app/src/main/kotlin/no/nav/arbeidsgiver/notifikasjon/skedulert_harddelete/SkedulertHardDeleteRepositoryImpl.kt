package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyTidStrategi.FORLENG
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.AggregateType
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.AggregateType.*
import no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete.SkedulertHardDeleteRepository.SkedulertHardDelete
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

interface SkedulertHardDeleteRepository {
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

    enum class AggregateType {
        Beskjed,
        Oppgave,
        Kalenderavtale,
        Sak,
    }

    suspend fun hentSkedulerteHardDeletes(
        tilOgMed: Instant,
    ): List<SkedulertHardDelete>

    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, kafkaTimestamp: Instant)

    suspend fun hardDelete(hardDelete: HendelseModel.HardDelete)

    suspend fun hent(aggregateId: UUID): SkedulertHardDelete?
}

class SkedulertHardDeleteRepositoryImpl(
    private val database: Database
) : SkedulertHardDeleteRepository {

    override suspend fun hentSkedulerteHardDeletes(
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

    override suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, kafkaTimestamp: Instant) {
        when (hendelse) {
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

            is HendelseModel.KalenderavtaleOpprettet -> {
                saveAggregate(hendelse, Kalenderavtale, hendelse.merkelapp, hendelse.grupperingsid)
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

            is HendelseModel.KalenderavtaleOppdatert -> {
                upsert(
                    aggregateId = hendelse.aggregateId,
                    opprettetTidspunkt = kafkaTimestamp,
                    hardDelete = hendelse.hardDelete,
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.HardDelete -> hardDelete(hendelse)

            is HendelseModel.NesteStegSak,
            is HendelseModel.TilleggsinformasjonSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.EksterntVarselKansellert,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.PåminnelseOpprettet,
            is HendelseModel.FristUtsatt,
            is HendelseModel.OppgavePåminnelseEndret,
            is HendelseModel.SoftDelete -> Unit

        }
    }

    override suspend fun hardDelete(hardDelete: HendelseModel.HardDelete) {
        database.transaction {
            if (hardDelete.merkelapp != null && hardDelete.grupperingsid != null) {
                executeUpdate("""
                    delete from aggregate where merkelapp = ? and grupperingsid = ?  
                """) {
                    text(hardDelete.merkelapp)
                    text(hardDelete.grupperingsid)
                }
            }

            executeUpdate("""
                delete from aggregate where aggregate_id = ?  
            """) {
                uuid(hardDelete.aggregateId)
            }
        }
    }

    override suspend fun hent(aggregateId: UUID): SkedulertHardDelete? {
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

    private fun ResultSet.toSkedulertHardDelete() =
        SkedulertHardDelete(
            aggregateId = getObject("aggregate_id", UUID::class.java),
            aggregateType = valueOf(getString("aggregate_type")),
            virksomhetsnummer = getString("virksomhetsnummer"),
            produsentid = getString("produsentid"),
            merkelapp = getString("merkelapp"),
            grupperingsid = getString("grupperingsid"),
            inputBase = getObject("input_base", OffsetDateTime::class.java),
            inputOm = getString("input_om")?.let { ISO8601Period.parse(it) },
            inputDen = getString("input_den")?.let { LocalDateTime.parse(it) },
            beregnetSlettetidspunkt = getObject("beregnet_slettetidspunkt", OffsetDateTime::class.java).toInstant(),
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
