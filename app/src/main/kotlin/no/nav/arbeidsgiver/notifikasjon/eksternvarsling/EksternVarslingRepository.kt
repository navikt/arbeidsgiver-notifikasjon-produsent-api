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

    private val podName = System.getenv("HOSTNAME")!!

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

    private fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: Hendelse.EksterntVarselFeilet) {
        /* attempt to update DB state so it's like kafka? */
    }

    private fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: Hendelse.EksterntVarselVellykket) {
        /* attempt to update DB state so it's like kafka? */
    }

    private fun oppdaterModellEtterHardDelete(hardDelete: Hendelse.HardDelete) {
        /* if notification already sent  {
         *     delete
         * } else {
         *     cancel?
         *     send when scheduled, and then delete?
         * }
         * */
    }

    private fun oppdaterModellEtterSoftDelete(softDelete: Hendelse.SoftDelete) {
        /* if notification  not sent yet {
         *    cancel?
         *    send when scheduled?
         * }
         */
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

    private suspend fun insertVarsler(
        varsler: List<EksterntVarsel>,
        produsentId: String,
        notifikasjonsId: UUID
    ) {
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
            INSERT INTO sms_varsel_kontaktinfo
            (
                varsel_id,
                notifikasjon_id,
                produsent_id,
                tlfnr,
                fnr_eller_orgnr,
                smsTekst,
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

    private fun lockRow(varselId: String) {
        database.transaction {
            executeCommand("""
                UPDATE sms_varsel_kontaktinfo
                SET 
                    locked_at = CURRENT_TIMESTAMP,
                    locked_by = ?,
                    locked_until = CURRENT_TIMESTAMP + CAST (? AS INTERVAL)
                WHERE varsel_id = ?
            """) {
                string(podName)
            }
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
}