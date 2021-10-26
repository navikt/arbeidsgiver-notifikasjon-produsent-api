package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

import no.nav.arbeidsgiver.notifikasjon.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
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
            is Hendelse.SoftDelete -> oppdaterModellEtterSoftDelete(hendelse)
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

    private fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: Hendelse.EksterntVarselFeilet) {
        /* attempt to update DB state so it's like kafka? */
        TODO()
    }

    private fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: Hendelse.EksterntVarselVellykket) {
        /* attempt to update DB state so it's like kafka? */
        TODO()
    }

    private fun oppdaterModellEtterHardDelete(hardDelete: Hendelse.HardDelete) {
        /* if notification already sent  {
         *     delete
         * } else {
         *     cancel?
         *     send when scheduled, and then delete?
         * }
         * */
        TODO()
    }

    private fun oppdaterModellEtterSoftDelete(softDelete: Hendelse.SoftDelete) {
        /* if notification  not sent yet {
         *    cancel?
         *    send when scheduled?
         * }
         */
        TODO()
    }


    private suspend fun insertVarsler(varsler: List<EksterntVarsel>, produsentId: String, notifikasjonsId: UUID) {
        /* Rewrite to batch insert? */
        database.transaction {
            for (varsel in varsler) {
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
        executeCommand("""
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
                altinn_response,
                
                locked_at,
                locked_by,
                locked_until
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
                NULL, /* altinn_response */
                NULL, /* locked_at */
                NULL, /* locked_by */
                NULL  /* locked_until */
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
        executeCommand("""
            INSERT INTO epost_varsel_kontaktinfo
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
                altinn_response,
                
                locked_at,
                locked_by,
                locked_until
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
                NULL, /* altinn_response */
                NULL, /* locked_at */
                NULL, /* locked_by */
                NULL  /* locked_until */
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


    suspend fun releaseTimedoutLocks(): List<UUID> {
        val unlocked = database.nonTransactionalCommand("""
            UPDATE ekstern_varsel_kontaktinfo
            SET locked = false
            WHERE locked = true
            RETURNING varsel_id, locked_by, locked_at
        """)
        return TODO("unlocked rows")
    }


    suspend fun finnOgLåsVarsel(): EksterntVarsel? {
        database.transaction {
            val varselId = runQuery("""
                        SELECT varsel_id
                        FROM sms_varsel_kontaktinfo 
                        WHERE 
                            locked = false
                            AND tilstand = 'NY'
                        LIMIT 1
                    """
            ) {
                getObject("varsel_id") as UUID
            }
                .firstOrNull()
                ?: return@transaction null

            executeCommand("""
                UPDATE sms_varsel_kontaktinfo
                SET 
                    locked = true,
                    locked_at = CURRENT_TIMESTAMP,
                    locked_by = ?,
                    locked_until = CURRENT_TIMESTAMP + CAST (? AS INTERVAL)
                WHERE varsel_id = ?
                
            """) {
                string(podName)
                TODO("locked_until offset")
                uuid(varselId)
            }

            return@transaction varselId
        }
    }
    private suspend fun <T> Transaction.withLockedRow(varselId: UUID, body: Transaction.() -> T) {
        val changedRows = executeCommand("""
            UPDATE sms_varsel_kontaktinfo
            SET 
                /* locked = true */
                locked_at = CURRENT_TIMESTAMP,
                locked_by = ?,
                locked_until = CURRENT_TIMESTAMP + CAST (? AS INTERVAL)
            WHERE varsel_id = ? /* AND locked = false */
        """) {
            string(podName)
            /* TODO: set the interval-type value. minutes? */
            uuid(varselId)
        }
        if (changedRows >= 1) {
            /* the row exists! */
            body().also {
                executeCommand("""
                    UPDATE sms_varsel_kontaktinfo
                    SET 
                        /* locked = false */
                    WHERE varsel_id = ?
                """) {
                    uuid(varselId)
                }
            }
        } else {
            /* no matching row. */
        }
    }
}