package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyTidStrategi.FORLENG
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class AutoSlettRepository(
    private val database: Database
) {
    val log = logger()

    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse, timestamp: Instant) {
        val ignored = when (hendelse) {
            is HendelseModel.BeskjedOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return

                upsert(
                    SkedulertHardDelete.fromHendelse(
                        hendelse = hendelse,
                        aggregateType = "Beskjed",
                        scheduledTime = ScheduledTime(
                            hardDelete,
                            hendelse.opprettetTidspunkt
                        ),
                        merkelapp = hendelse.merkelapp,
                    )
                )
            }

            is HendelseModel.OppgaveOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return

                upsert(
                    SkedulertHardDelete.fromHendelse(
                        hendelse = hendelse,
                        aggregateType = "Oppgave",
                        scheduledTime = ScheduledTime(
                            hardDelete,
                            hendelse.opprettetTidspunkt
                        ),
                        merkelapp = hendelse.merkelapp,
                    )
                )
            }

            is HendelseModel.SakOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return
                upsert(
                    SkedulertHardDelete.fromHendelse(
                        hendelse = hendelse,
                        aggregateType = "Sak",
                        scheduledTime = ScheduledTime(
                            hardDelete,
                            hendelse.opprettetTidspunkt
                        ),
                        merkelapp = hendelse.merkelapp,
                    )
                )
            }

            is HendelseModel.OppgaveUtført -> {
                val hardDelete = hendelse.hardDelete ?: return

                upsert(
                    SkedulertHardDelete.fromHendelse(
                        hendelse = hendelse,
                        aggregateType = "Oppgave",
                        scheduledTime = ScheduledTime(
                            hardDelete.nyTid,
                            timestamp.atOffset(ZoneOffset.UTC),
                        ),
                        merkelapp = "?", // TODO
                    ),
                    strategi = hendelse.hardDelete.strategi,
                    eksisterende = hent(hendelse.aggregateId),
                )
            }

            is HendelseModel.NyStatusSak -> {
                val hardDelete = hendelse.hardDelete ?: return
                upsert(
                    skedulertHardDelete = SkedulertHardDelete.fromHendelse(
                        hendelse = hendelse,
                        aggregateType = "Sak",
                        scheduledTime = ScheduledTime(
                            hardDelete.nyTid,
                            hendelse.opprettetTidspunkt
                        ),
                        merkelapp = "?", // TODO
                    ),
                    strategi = hendelse.hardDelete.strategi,
                    eksisterende = hent(hendelse.aggregateId),
                )
            }


            is HendelseModel.HardDelete -> hardDelete(hendelse.aggregateId)
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SoftDelete -> Unit
        }
    }

    private suspend fun hardDelete(aggregateId: UUID) {
        database.nonTransactionalExecuteUpdate("""
           delete from skedulert_hard_delete where aggregate_id = ? 
        """) {
            uuid(aggregateId)
        }
    }

    suspend fun hent(aggregateId: UUID): SkedulertHardDelete? {
        return database.nonTransactionalExecuteQuery("""
            select 
                aggregate_id,
                aggregate_type,
                virksomhetsnummer,
                produsentid,
                merkelapp,
                beregnet_slettetidspunkt,
                input_base,
                input_om,
                input_den 
            from skedulert_hard_delete 
            where aggregate_id = ?
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

    private suspend fun upsert(
        skedulertHardDelete: SkedulertHardDelete,
        strategi: HendelseModel.NyTidStrategi,
        eksisterende: SkedulertHardDelete?,
    ) {
        if (
            eksisterende != null &&
            strategi == FORLENG &&
            skedulertHardDelete.beregnetSlettetidspunkt.isBefore(eksisterende.beregnetSlettetidspunkt)
        ) {
            return
        }
        upsert(skedulertHardDelete)
    }

    private suspend fun upsert(skedulertHardDelete: SkedulertHardDelete) {
        database.nonTransactionalExecuteUpdate(
            """
                insert into skedulert_hard_delete (
                    aggregate_id, 
                    aggregate_type, 
                    virksomhetsnummer, 
                    produsentid, 
                    merkelapp, 
                    beregnet_slettetidspunkt, 
                    input_base, 
                    input_om, 
                    input_den           
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?) 
                    on conflict (aggregate_id) do 
                    update set 
                        beregnet_slettetidspunkt = EXCLUDED.beregnet_slettetidspunkt,
                        input_base = EXCLUDED.input_base,
                        input_om = EXCLUDED.input_om,
                        input_den = EXCLUDED.input_den;
                """
        ) {
            uuid(skedulertHardDelete.aggregateId)
            string(skedulertHardDelete.aggregateType)
            string(skedulertHardDelete.virksomhetsnummer)
            string(skedulertHardDelete.produsentid)
            string(skedulertHardDelete.merkelapp)
            timestamp_utc(skedulertHardDelete.beregnetSlettetidspunkt)
            timestamp_utc(skedulertHardDelete.inputBase)
            nullableString(skedulertHardDelete.inputOm?.toString())
            nullableString(skedulertHardDelete.inputDen?.toString())
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
) {
    companion object {
        fun fromHendelse(
            hendelse: HendelseModel.Hendelse,
            aggregateType: String,
            merkelapp: String,
            scheduledTime: ScheduledTime,
        ) = SkedulertHardDelete(
                aggregateId = hendelse.aggregateId,
                aggregateType = aggregateType,
                virksomhetsnummer = hendelse.virksomhetsnummer,
                produsentid = hendelse.produsentId ?: "ukjent",
                merkelapp = merkelapp,
                inputBase = scheduledTime.baseTime,
                inputOm = scheduledTime.omOrNull(),
                inputDen = scheduledTime.denOrNull(),
                beregnetSlettetidspunkt = scheduledTime.happensAt(),
            )
    }
}
