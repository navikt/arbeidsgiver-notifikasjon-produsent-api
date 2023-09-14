package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.sql.ResultSet
import java.time.*
import java.util.*

class EksternVarslingRepository(
    private val database: Database,
) {
    private val log = logger()
    private val podName = System.getenv("HOSTNAME") ?: "localhost"

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignore: Unit = when (hendelse) {
            is BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is PåminnelseOpprettet -> oppdaterModellEtterPåminnelseOpprettet(hendelse)
            is EksterntVarselFeilet -> oppdaterModellEtterEksterntVarselFeilet(hendelse)
            is EksterntVarselVellykket -> oppdaterModellEtterEksterntVarselVellykket(hendelse)
            is HardDelete -> oppdaterModellEtterHardDelete(hendelse)
            is SoftDelete -> {
                /* Garanterer sending (på samme måte som hard delete).
                 * Vi har ikke noe behov for å huske at den er soft-deleted i denne modellen, så
                 * dette er en noop.
                 */
            }
            is OppgaveUtført -> Unit
            is OppgaveUtgått -> Unit
            is BrukerKlikket -> Unit
            is SakOpprettet -> Unit
            is NyStatusSak -> Unit
        }
    }

    private suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: BeskjedOpprettet) {
        insertVarsler(
            varsler = beskjedOpprettet.eksterneVarsler,
            produsentId = beskjedOpprettet.produsentId,
            notifikasjonsId = beskjedOpprettet.notifikasjonId,
            notifikasjonOpprettet = beskjedOpprettet.opprettetTidspunkt,
        )
    }

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: OppgaveOpprettet) {
        insertVarsler(
            varsler = oppgaveOpprettet.eksterneVarsler,
            produsentId = oppgaveOpprettet.produsentId,
            notifikasjonsId = oppgaveOpprettet.notifikasjonId,
            notifikasjonOpprettet = oppgaveOpprettet.opprettetTidspunkt,
        )
    }

    private suspend fun oppdaterModellEtterPåminnelseOpprettet(påminnelseOpprettet: PåminnelseOpprettet) {
        insertVarsler(
            varsler = påminnelseOpprettet.eksterneVarsler,
            produsentId = påminnelseOpprettet.produsentId,
            notifikasjonsId = påminnelseOpprettet.notifikasjonId,
            notifikasjonOpprettet = påminnelseOpprettet.opprettetTidpunkt.atOffset(ZoneOffset.UTC),
        )
    }

    private suspend fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: EksterntVarselFeilet) {
        oppdaterUtfall(eksterntVarselFeilet.varselId, SendeStatus.FEIL, eksterntVarselFeilet.råRespons)
    }

    private suspend fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: EksterntVarselVellykket) {
        oppdaterUtfall(eksterntVarselVellykket.varselId, SendeStatus.OK, eksterntVarselVellykket.råRespons)
    }

    private suspend fun oppdaterUtfall(varselId: UUID, sendeStatus: SendeStatus, råRespons: JsonNode) {
        database.nonTransactionalExecuteUpdate("""
            update ekstern_varsel_kontaktinfo 
            set 
                altinn_response = ?::jsonb,
                state = '${EksterntVarselTilstand.KVITTERT}',
                sende_status = ?::status
            where
                varsel_id = ? 
        """) {
            jsonb(råRespons)
            text(sendeStatus.toString())
            uuid(varselId)
        }
    }

    private suspend fun oppdaterModellEtterHardDelete(hardDelete: HardDelete) {
        database.nonTransactionalExecuteUpdate("""
            insert into hard_delete (notifikasjon_id) values (?)
            on conflict do nothing
        """) {
            uuid(hardDelete.aggregateId)
        }
    }

    suspend fun deleteScheduledHardDeletes() {
        database.nonTransactionalExecuteUpdate("""
            delete from ekstern_varsel_kontaktinfo
            where state = '${EksterntVarselTilstand.KVITTERT}'
            and notifikasjon_id in (select notifikasjon_id from hard_delete)
        """)
    }

    private suspend fun insertVarsler(
        varsler: List<EksterntVarsel>,
        produsentId: String,
        notifikasjonsId: UUID,
        notifikasjonOpprettet: OffsetDateTime,
    ) {
        /* Rewrite to batch insert? */
        database.transaction {
            if (isHardDeleted(notifikasjonsId)) {
                return@transaction
            }
            for (varsel in varsler) {
                putOnJobQueue(varsel.varselId)
                @Suppress("UNUSED_VARIABLE") val ignored = when (varsel) {
                    is SmsVarselKontaktinfo -> insertSmsVarsel(
                        varsel = varsel,
                        produsentId = produsentId,
                        notifikasjonsId = notifikasjonsId,
                        notifikasjonOpprettet = notifikasjonOpprettet,
                    )
                    is EpostVarselKontaktinfo -> insertEpostVarsel(
                        varsel = varsel,
                        produsentId = produsentId,
                        notifikasjonsId = notifikasjonsId,
                        notifikasjonOpprettet = notifikasjonOpprettet,
                    )

                    is AltinntjenesteVarselKontaktinfo -> insertAltinntjenesteVarsel(
                        varsel = varsel,
                        produsentId = produsentId,
                        notifikasjonsId = notifikasjonsId,
                        notifikasjonOpprettet = notifikasjonOpprettet,
                    )
                }
            }
        }
    }

    private fun Transaction.isHardDeleted(notifikasjonsId: UUID) = executeQuery(
            """
            select 1 from hard_delete where notifikasjon_id = ?
            """,
            {
                uuid(notifikasjonsId)
            },
            { true }
        )
            .firstOrNull() ?: false

    private fun Transaction.insertSmsVarsel(
        varsel: SmsVarselKontaktinfo,
        notifikasjonsId: UUID,
        produsentId: String,
        notifikasjonOpprettet: OffsetDateTime,
    ) {
        executeUpdate("""
            INSERT INTO ekstern_varsel_kontaktinfo
            (
                varsel_id,
                notifikasjon_id,
                notifikasjon_opprettet,
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
                ?, /* notifikasjon_opprettet */
                ?, /* produsent_id */
                'SMS',
                ?, /* tlfnr */
                ?, /* fnr_eller_orgnr */
                ?, /* smsTekst */
                ?, /* sendevindu */
                ?, /* sendetidspunkt */
                'NY' /* tilstand */
            )
            ON CONFLICT DO NOTHING;
        """) {
            uuid(varsel.varselId)
            uuid(notifikasjonsId)
            timestamp_without_timezone_utc(notifikasjonOpprettet)
            text(produsentId)
            text(varsel.tlfnr)
            text(varsel.fnrEllerOrgnr)
            text(varsel.smsTekst)
            text(varsel.sendevindu.toString())
            nullableText(varsel.sendeTidspunkt?.toString())
        }
    }

    private fun Transaction.insertEpostVarsel(
        varsel: EpostVarselKontaktinfo,
        notifikasjonsId: UUID,
        produsentId: String,
        notifikasjonOpprettet: OffsetDateTime,
    ) {
        executeUpdate("""
            INSERT INTO ekstern_varsel_kontaktinfo
            (
                varsel_id,
                notifikasjon_id,
                notifikasjon_opprettet,
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
                ?, /* notifikasjon_opprettet */
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
            ON CONFLICT DO NOTHING;
        """) {
            uuid(varsel.varselId)
            uuid(notifikasjonsId)
            timestamp_without_timezone_utc(notifikasjonOpprettet)
            text(produsentId)
            text(varsel.epostAddr)
            text(varsel.fnrEllerOrgnr)
            text(varsel.tittel)
            text(varsel.htmlBody)
            text(varsel.sendevindu.toString())
            nullableText(varsel.sendeTidspunkt?.toString())
        }
    }

    private fun Transaction.insertAltinntjenesteVarsel(
        varsel: AltinntjenesteVarselKontaktinfo,
        notifikasjonsId: UUID,
        produsentId: String,
        notifikasjonOpprettet: OffsetDateTime,
    ) {
        executeUpdate("""
            INSERT INTO ekstern_varsel_kontaktinfo
            (
                varsel_id,
                notifikasjon_id,
                notifikasjon_opprettet,
                produsent_id,
                varsel_type,
                service_code,
                service_edition,
                fnr_eller_orgnr,
                tjeneste_tittel,
                tjeneste_innhold,
                sendevindu,
                sendetidspunkt,
                state
            )
            VALUES 
            (
                ?, /* varsel_id */
                ?, /* notifikasjon_id */
                ?, /* notifikasjon_opprettet */
                ?, /* produsent_id */
                'ALTINNTJENESTE',
                ?, /* service_code */
                ?, /* service_edition */
                ?, /* fnr_eller_orgnr */
                ?, /* tjeneste_tittel */
                ?, /* tjeneste_innhold */
                ?, /* sendevindu */
                ?, /* sendetidspunkt */
                'NY' /* tilstand */
            )
            ON CONFLICT DO NOTHING;
        """) {
            uuid(varsel.varselId)
            uuid(notifikasjonsId)
            timestamp_without_timezone_utc(notifikasjonOpprettet)
            text(produsentId)
            text(varsel.serviceCode)
            text(varsel.serviceEdition)
            text(varsel.virksomhetsnummer)
            text(varsel.tittel)
            text(varsel.innhold)
            text(varsel.sendevindu.toString())
            nullableText(varsel.sendeTidspunkt?.toString())
        }
    }

    data class ReleasedResource(
        val varselId: UUID,
        val lockedAt: LocalDateTime,
        val lockedBy: String,
    )

    suspend fun releaseTimedOutJobLocks(): List<ReleasedResource> {
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
                """select 1 from emergency_break limit 1""", transform = {}
            ).isEmpty()

            if (databaseIsEmpty) {
                log.error("database is empty, disabling processing")
                executeUpdate(
                    """
                        insert into emergency_break (id, stop_processing, detected_at)
                        values (0, true, CURRENT_TIMESTAMP)
                        on conflict (id) do update
                            set 
                                stop_processing = true,
                                detected_at = CURRENT_TIMESTAMP
                    """
                )
            }
        }
    }

    suspend fun emergencyBreakOn(): Boolean {
        return database.nonTransactionalExecuteQuery(
            """ select stop_processing from emergency_break where id = 0 """,
            transform = { getBoolean("stop_processing") }
        )
            .firstOrNull()
            ?: true
    }


    suspend fun createJobsForAbandonedVarsler() {
        database.nonTransactionalExecuteUpdate("""
            insert into job_queue (varsel_id, locked)
            (
                select varsel_id, false as locked from ekstern_varsel_kontaktinfo
                where 
                    state in ('${EksterntVarselTilstand.NY}', '${EksterntVarselTilstand.SENDT}')
                    and varsel_id not in (select varsel_id from job_queue)
            )
        """)
    }

    suspend fun findJob(lockTimeout: Duration): UUID? =
        database.nonTransactionalExecuteQuery("""
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
                    ORDER BY id
                    LIMIT 1
                    FOR UPDATE
                    SKIP LOCKED
                )
            RETURNING varsel_id
                """,
            setup = {
                text(podName)
                text(lockTimeout.toString())
            },
            transform = {
                getObject("varsel_id") as UUID
            }
        )
            .firstOrNull()

    suspend fun findVarsel(varselId: UUID): EksternVarselTilstand? {
        return database.nonTransactionalExecuteQuery(
            """
            select * from ekstern_varsel_kontaktinfo where varsel_id = ?
            """,
            setup = {
                uuid(varselId)
            },
            transform = {
                val data = EksternVarselStatiskData(
                    produsentId = getString("produsent_id"),
                    varselId = varselId,
                    notifikasjonId = getObject("notifikasjon_id", UUID::class.java),
                    eksternVarsel = when (val varselType = getString("varsel_type")) {
                        "SMS" -> EksternVarsel.Sms(
                            fnrEllerOrgnr = getString("fnr_eller_orgnr"),
                            sendeVindu = EksterntVarselSendingsvindu.valueOf(getString("sendevindu")),
                            sendeTidspunkt = getString("sendetidspunkt")?.let { LocalDateTime.parse(it) },
                            mobilnummer = getString("tlfnr"),
                            tekst = getString("sms_tekst"),
                        )
                        "EMAIL" -> EksternVarsel.Epost(
                            fnrEllerOrgnr = getString("fnr_eller_orgnr"),
                            sendeVindu = EksterntVarselSendingsvindu.valueOf(getString("sendevindu")),
                            sendeTidspunkt = getString("sendetidspunkt")?.let { LocalDateTime.parse(it) },
                            epostadresse = getString("epost_adresse"),
                            tittel = getString("tittel"),
                            body = getString("html_body")
                        )
                        "ALTINNTJENESTE" -> EksternVarsel.Altinntjeneste(
                            fnrEllerOrgnr = getString("fnr_eller_orgnr"),
                            sendeVindu = EksterntVarselSendingsvindu.valueOf(getString("sendevindu")),
                            sendeTidspunkt = getString("sendetidspunkt")?.let { LocalDateTime.parse(it) },
                            serviceCode = getString("service_code"),
                            serviceEdition = getString("service_edition"),
                            tittel = getString("tjeneste_tittel"),
                            innhold = getString("tjeneste_innhold")
                        )
                        else -> throw Error("Ukjent varsel_type '$varselType'")
                    }
                )

                when (val state = getString("state")) {
                    EksterntVarselTilstand.NY.toString() ->
                        EksternVarselTilstand.Ny(data)

                    EksterntVarselTilstand.SENDT.toString() ->
                        EksternVarselTilstand.Sendt(data, altinnResponse())

                    EksterntVarselTilstand.KVITTERT.toString() ->
                        EksternVarselTilstand.Kvittert(data, altinnResponse())

                    else -> throw Error("Ukjent tilstand '$state'")
                }
            })
            .firstOrNull()
    }

    private fun ResultSet.altinnResponse() = when (val sendeStatus = getString("sende_status")) {
        "OK" -> AltinnResponse.Ok(
            rå = laxObjectMapper.readTree(getString("altinn_response")),
        )

        "FEIL" -> AltinnResponse.Feil(
            rå = laxObjectMapper.readTree(getString("altinn_response")),
            feilkode = getString("altinn_feilkode"),
            feilmelding = getString("feilmelding"),
        )

        else -> throw Error("ukjent sende_status '$sendeStatus'")
    }


    suspend fun returnToJobQueue(varselId: UUID) {
        database.transaction {
            returnToJobQueue(varselId)
        }
    }

    private fun Transaction.returnToJobQueue(varselId: UUID) {
        deleteFromJobQueue(varselId)
        putOnJobQueue(varselId)
    }

    suspend fun deleteFromJobQueue(varselId: UUID) {
        database.transaction {
            deleteFromJobQueue(varselId)
        }
    }

    private fun Transaction.deleteFromJobQueue(varselId: UUID) {
        executeUpdate("""
            DELETE FROM job_queue WHERE varsel_id = ?
        """) {
            uuid(varselId)
        }
    }

    suspend fun markerSomKvittertAndDeleteJob(varselId: UUID) {
        database.transaction {
            executeUpdate(
                """
                    update ekstern_varsel_kontaktinfo
                    set state = '${EksterntVarselTilstand.KVITTERT}'
                    where varsel_id = ?
                """
            ) {
                uuid(varselId)
            }

            deleteFromJobQueue(varselId)
        }
    }

    suspend fun markerSomSendtAndReleaseJob(varselId: UUID, response: AltinnVarselKlientResponse) {
        database.transaction {
            executeUpdate(""" 
                update ekstern_varsel_kontaktinfo
                set 
                    state = '${EksterntVarselTilstand.SENDT}',
                    altinn_response = ?::jsonb,
                    sende_status = ?::status,
                    feilmelding = ?,
                    altinn_feilkode = ?
                where varsel_id = ?
            """) {
                jsonb(response.rå)
                when (response) {
                    is AltinnVarselKlientResponse.Ok -> {
                        text("OK")
                        nullableText(null)
                        nullableText(null)
                    }

                    is AltinnVarselKlientResponse.Feil -> {
                        text("FEIL")
                        nullableText(response.feilmelding)
                        nullableText(response.feilkode)
                    }
                }
                uuid(varselId)
            }

            returnToJobQueue(varselId)
        }
    }

    suspend fun scheduleJob(varselId: UUID, resumeAt: LocalDateTime) {
        database.transaction {
            executeUpdate(
                """
                insert into wait_queue (varsel_id, resume_job_at) 
                values (?, ?)
                """
            ) {
                uuid(varselId)
                timestamp_without_timezone(resumeAt)
            }
            executeUpdate(
                """
                delete from job_queue where varsel_id = ?
                """
            ) {
                uuid(varselId)
            }
        }
    }

    suspend fun rescheduleWaitingJobs(scheduledAt: LocalDateTime): Int {
        return database.nonTransactionalExecuteUpdate(
            """
                with selected as (
                    delete from wait_queue
                    where resume_job_at <= ?
                    returning varsel_id
                ) 
                insert into job_queue (varsel_id, locked) 
                select varsel_id, false as locked from selected
                on conflict do nothing
            """,
        ) {
            timestamp_without_timezone(scheduledAt)
        }
    }

    suspend fun jobQueueCount(): Int {
        return database.nonTransactionalExecuteQuery("""
            select count(*) as count from job_queue 
        """) {
            this.getInt("count")
        }.first()
    }

    suspend fun waitQueueCount(): Pair<Int, Int> {
        return database.nonTransactionalExecuteQuery("""
            select
                count(case when resume_job_at <= now() then 1 end) as past,
                count(case when resume_job_at > now() then 1 end) as future
            from wait_queue
        """) {
            this.getInt("past") to this.getInt("future")
        }.first()
    }

    suspend fun mottakerErPåAllowList(mottaker: String): Boolean {
        return database.nonTransactionalExecuteQuery("""
            select mottaker from allow_list 
            where mottaker = ?
            and skal_sendes = true
        """, {
            text(mottaker)
        }) {
            this.getString("mottaker")
        }.isNotEmpty()
    }

    suspend fun updateEmergencyBrakeTo(newState: Boolean) {
        database.nonTransactionalExecuteUpdate("""
            insert into emergency_break (id, stop_processing, detected_at)
            values (0, ?, CURRENT_TIMESTAMP)
            on conflict (id) do update
                set 
                    stop_processing = ?,
                    detected_at = CURRENT_TIMESTAMP
        """) {
            boolean(newState)
            boolean(newState)
        }
    }

    suspend fun recordAltinnVarselKlientError(varselId: UUID, altinnVarselKlientResponse: AltinnVarselKlientResponseOrException) {
        when (altinnVarselKlientResponse) {
            is AltinnVarselKlientResponse.Ok -> {}
            is AltinnVarselKlientResponse.Feil -> {
                database.nonTransactionalExecuteUpdate("""
                    insert into altinn_response_log (varsel_id, timestamp, altinn_feil, altinn_response)
                    values (?, ?, ?, ?::jsonb)
                """) {
                    uuid(varselId)
                    instantAsText(Instant.now())
                    text(altinnVarselKlientResponse.feilkode)
                    jsonb(altinnVarselKlientResponse.rå)
                }
            }
            is UkjentException -> {
                database.nonTransactionalExecuteUpdate("""
                    insert into altinn_response_log (varsel_id, timestamp, exception)
                    values (?, ?, ?)
                """) {
                    uuid(varselId)
                    instantAsText(Instant.now())
                    text(altinnVarselKlientResponse.exception.stackTraceToString())
                }
            }
        }
    }
}

internal fun Transaction.putOnJobQueue(varselId: UUID) {
    executeUpdate(
        """
            insert into job_queue(varsel_id, locked) values (?, false)
            on conflict do nothing;
        """
    ) {
        uuid(varselId)
    }
}
