package no.nav.arbeidsgiver.notifikasjon.autoslett

import no.nav.arbeidsgiver.notifikasjon.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class AutoSlettRepository(
    private val database: Database
) {
    val log = logger()

    suspend fun oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse) {
        val ignored = when (hendelse) {
            is HendelseModel.BeskjedOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return

                lagre(
                    OpprettAutoSlett(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = "Beskjed",
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        inputBase = hendelse.opprettetTidspunkt.toInstant(),
                        inputOm = hardDelete.omOrNull(),
                        inputDen = hardDelete.denOrNull(),
                        beregnetSlettetidspunkt = ScheduledTime(hardDelete, hendelse.opprettetTidspunkt.toInstant()),
                    )
                )
            }
            is HendelseModel.OppgaveOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return

                lagre(
                    OpprettAutoSlett(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = "Oppgave",
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        inputBase = hendelse.opprettetTidspunkt.toInstant(),
                        inputOm = hardDelete.omOrNull(),
                        inputDen = hardDelete.denOrNull(),
                        beregnetSlettetidspunkt = ScheduledTime(hardDelete, hendelse.opprettetTidspunkt.toInstant()),
                    )
                )
            }
            is HendelseModel.SakOpprettet -> {
                val hardDelete = hendelse.hardDelete ?: return

                lagre(
                    OpprettAutoSlett(
                        aggregateId = hendelse.aggregateId,
                        aggregateType = "Sak",
                        virksomhetsnummer = hendelse.virksomhetsnummer,
                        produsentid = hendelse.produsentId,
                        merkelapp = hendelse.merkelapp,
                        inputBase = hendelse.opprettetTidspunkt.toInstant(),
                        inputOm = hardDelete.omOrNull(),
                        inputDen = hardDelete.denOrNull(),
                        beregnetSlettetidspunkt = ScheduledTime(hardDelete, hendelse.opprettetTidspunkt.toInstant()),
                    )
                )
            }

            is HendelseModel.OppgaveUtført,
            is HendelseModel.NyStatusSak -> {
                TODO("hvis finnes ta hensyn til strategi")
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

    private suspend fun lagre(opprettAutoSlett: OpprettAutoSlett) {
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
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING 
                """
        ) {
            uuid(opprettAutoSlett.aggregateId)
            string(opprettAutoSlett.aggregateType)
            string(opprettAutoSlett.virksomhetsnummer)
            string(opprettAutoSlett.produsentid)
            string(opprettAutoSlett.merkelapp)
            timestamp_utc(opprettAutoSlett.beregnetSlettetidspunkt.happensAt())
            timestamp_utc(opprettAutoSlett.inputBase)
            nullableString(opprettAutoSlett.inputOm?.toString())
            nullableString(opprettAutoSlett.inputDen?.toString())
        }
    }
}

data class OpprettAutoSlett(
    val aggregateId: UUID,
    val aggregateType: String,
    val virksomhetsnummer: String,
    val produsentid: String,
    val merkelapp: String,
    val inputBase: Instant,
    val inputOm: ISO8601Period?,
    val inputDen: LocalDateTime?,
    val beregnetSlettetidspunkt: ScheduledTime,
)
