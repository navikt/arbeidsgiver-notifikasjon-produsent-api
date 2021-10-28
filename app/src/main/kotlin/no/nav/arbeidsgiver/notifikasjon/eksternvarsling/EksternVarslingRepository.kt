package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.arbeidsgiver.notifikasjon.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import java.time.LocalDateTime
import java.util.*

class EksternVarslingRepository(
    private val database: Database
) {

    private val podName = System.getenv("HOSTNAME") ?: "localhost"

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        val ignore: Unit = when (hendelse) {
            is Hendelse.BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is Hendelse.OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is Hendelse.EksterntVarselFeilet -> oppdaterModellEtterEksterntVarselFeilet(hendelse)
            is Hendelse.EksterntVarselVellykket -> oppdaterModellEtterEksterntVarselVellykket(hendelse)
            is Hendelse.HardDelete -> oppdaterModellEtterHardDelete(hendelse)
            is Hendelse.SoftDelete -> {
                /* Garanterer sending (på samme måte som hard delete).
                 * Vi har ikke noe behov for å huske at den er soft-deleted i denne modellen, så
                 * dette er en noop.
                 */
            }
            is Hendelse.OppgaveUtført -> Unit
            is Hendelse.BrukerKlikket -> Unit
        }
    }

    private suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: Hendelse.BeskjedOpprettet) {
        insertVarsler(
            varsler = beskjedOpprettet.eksterneVarsler,
            produsentId = beskjedOpprettet.produsentId,
            notifikasjonsId = beskjedOpprettet.notifikasjonId,
        )
    }

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: Hendelse.OppgaveOpprettet) {
        insertVarsler(
            varsler = oppgaveOpprettet.eksterneVarsler,
            produsentId = oppgaveOpprettet.produsentId,
            notifikasjonsId = oppgaveOpprettet.notifikasjonId,
        )
    }

    private suspend fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: Hendelse.EksterntVarselFeilet) {
        oppdaterUtfall(eksterntVarselFeilet.varselId, eksterntVarselFeilet.råRespons)
    }

    private suspend fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: Hendelse.EksterntVarselVellykket) {
        oppdaterUtfall(eksterntVarselVellykket.varselId, eksterntVarselVellykket.råRespons)
    }

    private suspend fun oppdaterUtfall(varselId: UUID, råRespons: JsonNode) {
        database.nonTransactionalExecuteUpdate("""
            update ekstern_varsel_kontaktinfo 
            set 
                altinn_response = ?::jsonb,
                tilstand = '${VarselTilstand.KVITTERT}' 
            where
                varsel_id = ? 
                and tilstand <> '${VarselTilstand.KVITTERT}'
        """) {
            jsonb(råRespons)
            uuid(varselId)
        }
    }

    private suspend fun oppdaterModellEtterHardDelete(hardDelete: Hendelse.HardDelete) {
        database.nonTransactionalExecuteUpdate("""
            update ekstern_varsel_kontaktinfo
            set hard_deleted = true
            where notifikasjon_id = ?
        """) {
            uuid(hardDelete.notifikasjonId)
        }
    }

    suspend fun deleteCompletedVarsler() {
        database.nonTransactionalExecuteUpdate("""
            delete from ekstern_varsel_kontaktinfo
            where hard_deleted and tilstand = '${VarselTilstand.KVITTERT}'
        """)
    }

    private suspend fun insertVarsler(varsler: List<EksterntVarsel>, produsentId: String, notifikasjonsId: UUID) {
        /* Rewrite to batch insert? */
        database.transaction {
            for (varsel in varsler) {
                executeUpdate("""
                    insert into work_queue(varsel_id, locked) values (?, false);
                """) {
                    uuid(varsel.varselId)
                }
                when (varsel) {
                    is SmsVarselKontaktinfo -> insertSmsVarsel(
                        varsel = varsel,
                        produsentId = produsentId,
                        notifikasjonsId = notifikasjonsId
                    )
                    is EpostVarselKontaktinfo -> insertEpostVarsel(
                        varsel = varsel,
                        produsentId = produsentId,
                        notifikasjonsId = notifikasjonsId
                    )
                }
            }
        }
    }

    private fun Transaction.insertSmsVarsel(
        varsel: SmsVarselKontaktinfo,
        notifikasjonsId: UUID,
        produsentId: String,
    ) {
        executeUpdate("""
            INSERT INTO ekstern_varsel_kontaktinfo
            (
                varsel_id,
                notifikasjon_id,
                produsent_id,
                tlfnr,
                fnr_eller_orgnr,
                sms_tekst,
                sendevindu,
                sendetidspunkt,

                tilstand,
                altinn_response
            )
            VALUES 
            (
                ?, /* varsel_id */
                ?, /* notifikasjon_id */
                ?, /* produsent_id */
                ?, /* tlfnr */
                ?, /* fnr_eller_orgnr */
                ?, /* smsTekst */
                ?, /* sendevindu */
                ?, /* sendetidspunkt */
                'NY', /* tilstand */
                NULL /* altinn_response */
            )
            ON CONFLICT (varsel_id) DO NOTHING;
        """) {
            uuid(varsel.varselId)
            uuid(notifikasjonsId)
            string(produsentId)
            string(varsel.tlfnr)
            string(varsel.fnrEllerOrgnr)
            string(varsel.smsTekst)
            string(varsel.sendevindu.toString())
            nullableTimestamp(varsel.sendeTidspunkt)
        }
    }

    private fun Transaction.insertEpostVarsel(
        varsel: EpostVarselKontaktinfo,
        notifikasjonsId: UUID,
        produsentId: String,
    ) {
        executeUpdate("""
            INSERT INTO ekstern_varsel_kontaktinfo
            (
                varsel_id,
                notifikasjon_id,
                produsent_id,
                epost_adresse,
                fnr_eller_orgnr,
                tittel,
                html_body,
                sendevindu,
                sendetidspunkt,

                tilstand,
                altinn_response
            )
            VALUES 
            (
                ?, /* varsel_id */
                ?, /* notifikasjon_id */
                ?, /* produsent_id */
                ?, /* epost_adresse */
                ?, /* fnr_eller_orgnr */
                ?, /* tittel */
                ?, /* html_body */
                ?, /* sendevindu */
                ?, /* sendetidspunkt */
                'NY', /* tilstand */
                NULL /* altinn_response */
            )
            ON CONFLICT (varsel_id) DO NOTHING;
        """) {
            uuid(varsel.varselId)
            uuid(notifikasjonsId)
            string(produsentId)
            string(varsel.epostAddr)
            string(varsel.fnrEllerOrgnr)
            string(varsel.tittel)
            string(varsel.htmlBody)
            string(varsel.sendevindu.toString())
            nullableTimestamp(varsel.sendeTidspunkt)
        }
    }

    data class ReleasedResource(
        val varselId: UUID,
        val lockedAt: LocalDateTime,
        val lockedBy: String
    )

    suspend fun releaseAbandonedLocks(): List<ReleasedResource> {
        return database.nonTransactionalExecuteQuery("""
            UPDATE work_queue
            SET locked = false
            WHERE locked = true AND locked_until < CURRENT_TIMESTAMP
            RETURNING varsel_id, locked_by, locked_at
        """) {
            ReleasedResource(
                varselId = getObject("varsel_id", UUID::class.java),
                lockedAt = getTimestamp("locked_at").toLocalDateTime(),
                lockedBy = getString("locked_by"),
            )
        }
    }


    suspend fun findWork(): UUID? {
        return database.nonTransactionalExecuteQuery("""
                UPDATE work_queue
                SET locked = true,
                    locked_by = ?,
                    locked_at = CURRENT_TIMESTAMP,
                    locked_until = CURRENT_TIMESTAMP + ?
                WHERE 
                    id = (
                        SELECT id FROM work_queue 
                        WHERE 
                            locked = false
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                    )
                RETURNING varsel_id
                    """,
                setup = {
                    string(podName)
                    // locked_until offset

                },
                transform = {
                    getObject("varsel_id") as UUID
                }
            )
                .firstOrNull()
    }

    fun Transaction.returnJobToWorkQueue(varselId: UUID) {
        executeUpdate("""
            UPDATE work_queue
            SET locked = false
            WHERE varsel_id = ?
        """) {
            uuid(varselId)
        }
    }

    fun Transaction.deleteJobFromWorkQueue(varselId: UUID) {
        executeUpdate("""
            DELETE FROM work_queue WHERE varsel_id = ?
        """) {
            uuid(varselId)
        }
    }
}