package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyTidStrategi.FORLENG
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class AutoSlettRepository(
    private val database: Database
) {
    val log = logger()

    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse) {
        val ignored = when (hendelse) {
            is HendelseModel.BeskjedOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return

                val aggregateType = "Beskjed"
                val baseTime = hendelse.opprettetTidspunkt.toInstant()
                val scheduledTime = ScheduledTime(hardDelete, baseTime)

                upsert(
                    SkedulertHardDelete(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = aggregateType,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        beregnetSlettetidspunkt = scheduledTime.happensAt(),
                        inputBase = baseTime,
                        inputOm = hardDelete.omOrNull(),
                        inputDen = hardDelete.denOrNull(),
                    )
                )
            }

            is HendelseModel.OppgaveOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return
                val aggregateType = "Oppgave"

                val baseTime = hendelse.opprettetTidspunkt.toInstant()
                val scheduledTime = ScheduledTime(hardDelete, baseTime)

                upsert(
                    SkedulertHardDelete(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = aggregateType,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        inputBase = baseTime,
                        inputOm = hardDelete.omOrNull(),
                        inputDen = hardDelete.denOrNull(),
                        beregnetSlettetidspunkt = scheduledTime.happensAt(),
                    )
                )
            }

            is HendelseModel.SakOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return

                val aggregateType = "Sak"
                val baseTime = hendelse.opprettetTidspunkt.toInstant()
                val scheduledTime = ScheduledTime(hardDelete, baseTime)
                upsert(
                    SkedulertHardDelete(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = aggregateType,
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        inputBase = baseTime,
                        inputOm = hardDelete.omOrNull(),
                        inputDen = hardDelete.denOrNull(),
                        beregnetSlettetidspunkt = scheduledTime.happensAt(),
                    )
                )
            }

            is HendelseModel.OppgaveUtført -> {
                val hardDelete = hendelse.hardDelete ?: return

                val baseTime = Instant.now() // TODO: fix!
                val scheduledTime = ScheduledTime(hardDelete.nyTid, baseTime)
                val beregnetSlettetidspunkt = scheduledTime.happensAt()

                val eksisterende = hent(hendelse.aggregateId)

                if (
                    eksisterende != null &&
                    hendelse.hardDelete.strategi == FORLENG &&
                    beregnetSlettetidspunkt.isBefore(eksisterende.beregnetSlettetidspunkt)
                ) {
                    return
                }

                upsert(
                    SkedulertHardDelete(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = "Oppgave",
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = "?", // TODO: hendelse.merkelapp,
                        inputBase = baseTime,
                        inputOm = hardDelete.nyTid.omOrNull(),
                        inputDen = hardDelete.nyTid.denOrNull(),
                        beregnetSlettetidspunkt = beregnetSlettetidspunkt,
                    )
                )
            }

            is HendelseModel.NyStatusSak -> {
                val hardDelete = hendelse.hardDelete ?: return

                val baseTime = hendelse.opprettetTidspunkt.toInstant()
                val scheduledTime = ScheduledTime(hardDelete.nyTid, baseTime)
                val beregnetSlettetidspunkt = scheduledTime.happensAt()

                val eksisterende = hent(hendelse.aggregateId)

                if (
                    eksisterende != null &&
                    hendelse.hardDelete.strategi == FORLENG &&
                    beregnetSlettetidspunkt.isBefore(eksisterende.beregnetSlettetidspunkt)
                ) {
                    return
                }

                upsert(
                    SkedulertHardDelete(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = "Oppgave",
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = "?", // TODO: hendelse.merkelapp,
                        inputBase = baseTime,
                        inputOm = hardDelete.nyTid.omOrNull(),
                        inputDen = hardDelete.nyTid.denOrNull(),
                        beregnetSlettetidspunkt = beregnetSlettetidspunkt,
                    )
                )
            }


            is HendelseModel.HardDelete -> {
                TODO("HER SKAL RADEN SLETTES!")
            }

            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket,
            is HendelseModel.BrukerKlikket -> Unit
            is HendelseModel.SoftDelete -> Unit
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
                inputBase = getObject("input_base", OffsetDateTime::class.java).toInstant(),
                inputOm = getString("input_om")?.let { ISO8601Period.parse(it) },
                inputDen = getString("input_den")?.let { LocalDateTime.parse(it) },
                beregnetSlettetidspunkt = getObject("beregnet_slettetidspunkt", OffsetDateTime::class.java).toInstant(),
            )
        }.firstOrNull()
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
    val inputBase: Instant,
    val inputOm: ISO8601Period?,
    val inputDen: LocalDateTime?,
    val beregnetSlettetidspunkt: Instant,
)
