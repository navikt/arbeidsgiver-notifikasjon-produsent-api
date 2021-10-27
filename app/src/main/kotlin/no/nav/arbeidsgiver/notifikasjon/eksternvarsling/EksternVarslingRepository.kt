package no.nav.arbeidsgiver.notifikasjon.eksternvarsling

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

    private fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: Hendelse.EksterntVarselFeilet) {
        /* attempt to update DB state so it's like kafka? */
        TODO()
    }

    private fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: Hendelse.EksterntVarselVellykket) {
        /* attempt to update DB state so it's like kafka? */
        /* replay (med empty database) */
        TODO()
    }

    /* TODO: skal vi håndtere at man tømmer database og null-stiller kafka consumer group?
     * - forhindre re-sending ved å "detektere" at man ikke er ajour med kafka-strømmen.
     *
     * - basert på kafka-event-metadata, så kan vi se hvor "gammel" en hendelse er.
     *
     * 1. hvis sendingen ble produsert for lenge siden, marker, ikke send, vent på manuel intervention.
     *   + enkelt system
     *   + mulig å være vilkårlig presis
     *   - manuelt, fare for menneskelig feil
     *   - manuelt, kan gå med mye tid
     *   - manuelt, kan være mye delay før avgjørelse blir tatt
     *   - manuelt, kan være så mye jobb at vi ikke får prioritert det
     *
     * 2. hvis sendingen ble produsert for lenge siden, marker, ikke send.
     *    ha en prosess, som automatisk gjør avgjørelsen.
     *
     *   + Vi sender i hvert fall ikke gamle ting.
     *   - Vi får dobbletsending av nye ting.
     *   - Heurestikk for å avgjøre om noe skal gjøres, som blir vanskeligere jo
     *     nærmere i tid hendelsen ble sent. Ingen klar overgang fra "gamelt" til "nytt".
     *
     * 3. detekter rebuild. ikke prossesr jobs.
     *  - (eldste kafka tid) - (sist lest kafka-tid)
     *
     * 4. oppgi snapshot (event id), og ikke utfør jobs før man er forbi den.
     *   + utvetydig
     *   + stopp-proessesering-logikk er enkel
     *   - huske-hvor-man-stopper er vanskelig
     *   - fungerer dårlig ved ikke-planlagt reset (id-en man venter til er ikke oppdatert)
     *   - hvis man skal håndtere mister databasen, så må backup-offsettet være lagret utenfor db og kafka.
     *   - vi må, på et eller annet vis, huske event-id-er. Per partisjon? Eller offset per partisjon?
     *      sjekke logger
     *      sjekke kafka-offsets fra gammel consumer group hvis man bytter consumer-group
     */

    private fun oppdaterModellEtterHardDelete(hardDelete: Hendelse.HardDelete) {
        /* "guarantees" complete sending. hard delete afterwards. */
        TODO()
    }

    // TODO: huske å potensielt implementere cancel-event. jira?
    // TODO: dokumentasjon for soft/hard delete: Stopper ikke sending av varsler.

    private suspend fun insertVarsler(varsler: List<EksterntVarsel>, produsentId: String, notifikasjonsId: UUID) {
        /* Rewrite to batch insert? */
        database.transaction {
            for (varsel in varsler) {
                executeCommand("""
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
        return database.runNonTransactionalQuery("""
            UPDATE work_queue
            SET locked = false
            WHERE locked = true AND locked_until < CURRENT_TIMESTAMP
            RETURNING varsel_id, locked_by, locked_at
        """) {
            ReleasedResource(
                varselId = getObject("varselId", UUID::class.java),
                lockedAt = getTimestamp("locked_at").toLocalDateTime(),
                lockedBy = getString("locked_by"),
            )
        }
    }


    suspend fun findWork(): EksterntVarsel? {
        return database.queryCommand("""
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
                inject = {
                    string(podName)
                    // locked_until offset

                },
                extract = {
                    getObject("varsel_id") as UUID
                }
            )
                .firstOrNull()
    }

    fun Transaction.returnJobToWorkQueue(varselId: UUID) {
        executeCommand("""
            UPDATE job_queue
            SET locked = false
            WHERE varsel_id = ?
        """) {
            uuid(varselId)
        }
    }

    fun Transaction.deleteJobFromWorkQueue(varselId: UUID) {
        executeCommand("""
            DELETE FROM job_queue WHERE varsel_id = ?
        """) {
            uuid(varselId)
        }
    }
}