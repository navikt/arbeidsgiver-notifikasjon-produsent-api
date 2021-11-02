package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.arbeidsgiver.notifikasjon.EksterntVarsel as EksterntVarselBestilling
import no.nav.arbeidsgiver.notifikasjon.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class EksternVarslingRepository(
    private val database: Database
) {
    private val log = logger()
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
                state = '${EksterntVarselTilstand.KVITTERT}' 
            where
                varsel_id = ? 
                and state <> '${EksterntVarselTilstand.KVITTERT}'
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

    suspend fun deleteScheduledHardDeletes() {
        database.nonTransactionalExecuteUpdate("""
            delete from ekstern_varsel_kontaktinfo
            where hard_deleted and state = '${EksterntVarselTilstand.KVITTERT}'
        """)
    }

    private suspend fun insertVarsler(
        varsler: List<EksterntVarselBestilling>,
        produsentId: String,
        notifikasjonsId: UUID
    ) {
        /* Rewrite to batch insert? */
        database.transaction {
            for (varsel in varsler) {
                executeUpdate("""
                    insert into job_queue(varsel_id, locked) values (?, false);
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
                varsel_type,
                tlfnr,
                fnr_eller_orgnr,
                sms_tekst,
                sendevindu,
                sendetidspunkt,
                state
            )
            VALUES 
            (
                ?, /* varsel_id */
                ?, /* notifikasjon_id */
                ?, /* produsent_id */
                'SMS',
                ?, /* tlfnr */
                ?, /* fnr_eller_orgnr */
                ?, /* smsTekst */
                ?, /* sendevindu */
                ?, /* sendetidspunkt */
                'NY' /* tilstand */
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
                varsel_type,
                epost_adresse,
                fnr_eller_orgnr,
                tittel,
                html_body,
                sendevindu,
                sendetidspunkt,
                state
            )
            VALUES 
            (
                ?, /* varsel_id */
                ?, /* notifikasjon_id */
                ?, /* produsent_id */
                'EMAIL',
                ?, /* epost_adresse */
                ?, /* fnr_eller_orgnr */
                ?, /* tittel */
                ?, /* html_body */
                ?, /* sendevindu */
                ?, /* sendetidspunkt */
                'NY' /* tilstand */
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
            UPDATE job_queue
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


    suspend fun detectEmptyDatabase() {
        database.transaction {
            val databaseIsEmpty = executeQuery(
                """select 1 from ekstern_varsel_kontaktinfo limit 1""", transform = {}
            ).isEmpty()

            if (databaseIsEmpty) {
                log.error("database is empty, disabling processing")
                executeUpdate(
                    """
                        insert into emergency_break (id, stop_processing)
                        values (0, true)
                        on conflict (id) do update
                            set stop_processing = true
                    """
                )
            }
        }
    }

    suspend fun processingDisabled(): Boolean {
        return database.nonTransactionalExecuteQuery(
            """ select stop_processing from emergency_break where id = 0 """,
            transform = { getBoolean("stop_processing") }
        )
            .firstOrNull()
            ?: true
    }


    suspend fun requeueAbandonedWork() {
        database.nonTransactionalExecuteUpdate("""
            --- todo
            insert into job_queue (varsel_id, locked)
            from 
            (
                select varsel_id from ekstern_varsel_kontaktinfo
                where 
                    state <> '${EksterntVarselTilstand.SENDT}'
                    and varsel_id not in (select varsel_id from job_queue)
            )
        """)
    }


    suspend fun findWork(lockTimeout: Duration): UUID? {
        return database.nonTransactionalExecuteQuery("""
                UPDATE job_queue
                SET locked = true,
                    locked_by = ?,
                    locked_at = CURRENT_TIMESTAMP,
                    locked_until = CURRENT_TIMESTAMP + ?::interval
                WHERE 
                    id = (
                        SELECT id FROM job_queue 
                        WHERE 
                            locked = false
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                    )
                RETURNING varsel_id
                    """,
                setup = {
                    string(podName)
                    string(lockTimeout.toString())

                },
                transform = {
                    getObject("varsel_id") as UUID
                }
            )
                .firstOrNull()
    }

    suspend fun findVarsel(varselId: UUID): EksternVarselTilstand? {
        database.nonTransactionalExecuteQuery(
            """
            select * from ekstern_varsel_kontaktinfo where varsel_id = ?
            """,
            setup = {
            },
            transform = {
            })
    }


    suspend fun returnToWorkQueue(varselId: UUID) {
        database.transaction {
            returnToWorkQueue(varselId)
        }
    }

    fun Transaction.returnToWorkQueue(varselId: UUID) {
        executeUpdate("""
            UPDATE job_queue
            SET locked = false
            WHERE varsel_id = ?
        """) {
            uuid(varselId)
        }
    }

    suspend fun deleteFromWorkQueue(varselId: UUID) {
        database.transaction {
            deleteFromWorkQueue(varselId)
        }
    }

    fun Transaction.deleteFromWorkQueue(varselId: UUID) {
        executeUpdate("""
            DELETE FROM job_queue WHERE varsel_id = ?
        """) {
            uuid(varselId)
        }
    }

    suspend fun storeAndDelete(v: EksternVarselTilstand.Kvittert) {

    }

    suspend fun storeAndRelease(v: EksternVarselTilstand.Utført) {
        database.transaction {
            executeUpdate(""" 
                update ekstern_varsel_kontaktinfo
                set 
                    altinn_response = ?::jsonb,
                    altinn_utfall = ?,
                    altinn_feilmelding = ?,
                    tilstand = '${EksterntVarselTilstand.SENDT}'
                where varsel_id = ?
            """) {
                jsonb(TODO())
                string(TODO())
                string(TODO())
                uuid(v.varselId)
            }

            executeUpdate(""" 
                update job_queue
                set locked = false
                where varsel_id = ?
            """) {
                uuid(v.varselId)
            }
        }
    }
}