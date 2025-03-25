package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.JsonNode
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnressursVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselKansellert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOppdatert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand.AVLYST
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NesteStegSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.TilleggsinformasjonSak
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Transaction
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class EksternVarslingRepository(
    private val database: Database,
) {
    private val log = logger()
    private val podName = System.getenv("HOSTNAME") ?: "localhost"

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        when (hendelse) {
            is BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is KalenderavtaleOpprettet -> oppdaterModellEtterKalenderavtaleOpprettet(hendelse)
            is KalenderavtaleOppdatert -> oppdaterModellEtterKalenderavtaleOppdatert(hendelse)
            is PåminnelseOpprettet -> oppdaterModellEtterPåminnelseOpprettet(hendelse)
            is EksterntVarselFeilet -> oppdaterModellEtterEksterntVarselFeilet(hendelse)
            is EksterntVarselVellykket -> oppdaterModellEtterEksterntVarselVellykket(hendelse)
            is EksterntVarselKansellert -> oppdaterModellEtterEksterntVarselKansellert(hendelse)
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
            is NesteStegSak -> Unit
            is TilleggsinformasjonSak -> Unit
            is FristUtsatt -> Unit
            is HendelseModel.OppgavePåminnelseEndret -> Unit
        }
    }

    private suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: BeskjedOpprettet) {
        /* Rewrite to batch insert? */
        database.transaction {
            insertVarsler(
                notifikasjonsId = beskjedOpprettet.notifikasjonId,
                varsler = beskjedOpprettet.eksterneVarsler,
                produsentId = beskjedOpprettet.produsentId,
                notifikasjonOpprettet = beskjedOpprettet.opprettetTidspunkt
            ) { tx: Transaction ->
                tx.executeUpdate(
                    """
                insert into merkelapp_grupperingsid_notifikasjon (merkelapp, grupperingsid, notifikasjon_id)
                values (?, ?, ?)
                on conflict do nothing
                """
                ) {
                    text(beskjedOpprettet.merkelapp)
                    nullableText(beskjedOpprettet.grupperingsid)
                    uuid(beskjedOpprettet.notifikasjonId)
                }
            }
        }
    }

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: OppgaveOpprettet) {
        /* Rewrite to batch insert? */
        database.transaction {
            insertVarsler(
                notifikasjonsId = oppgaveOpprettet.notifikasjonId,
                varsler = oppgaveOpprettet.eksterneVarsler,
                produsentId = oppgaveOpprettet.produsentId,
                notifikasjonOpprettet = oppgaveOpprettet.opprettetTidspunkt
            ) { tx: Transaction ->
                tx.executeUpdate(
                    """
                insert into merkelapp_grupperingsid_notifikasjon (merkelapp, grupperingsid, notifikasjon_id)
                values (?, ?, ?)
                on conflict do nothing
                """
                ) {
                    text(oppgaveOpprettet.merkelapp)
                    nullableText(oppgaveOpprettet.grupperingsid)
                    uuid(oppgaveOpprettet.notifikasjonId)
                }
            }
        }
    }

    private suspend fun oppdaterModellEtterKalenderavtaleOpprettet(kalenderavtaleOpprettet: KalenderavtaleOpprettet) {
        /* Rewrite to batch insert? */
        database.transaction {
            insertVarsler(
                notifikasjonsId = kalenderavtaleOpprettet.notifikasjonId,
                varsler = kalenderavtaleOpprettet.eksterneVarsler,
                produsentId = kalenderavtaleOpprettet.produsentId,
                notifikasjonOpprettet = kalenderavtaleOpprettet.opprettetTidspunkt
            ) { tx: Transaction ->
                tx.executeUpdate(
                    """
                insert into merkelapp_grupperingsid_notifikasjon (merkelapp, grupperingsid, notifikasjon_id)
                values (?, ?, ?)
                on conflict do nothing
                """
                ) {
                    text(kalenderavtaleOpprettet.merkelapp)
                    nullableText(kalenderavtaleOpprettet.grupperingsid)
                    uuid(kalenderavtaleOpprettet.notifikasjonId)
                }
            }
        }
    }

    private suspend fun oppdaterModellEtterKalenderavtaleOppdatert(hendelse: KalenderavtaleOppdatert) {
        if (hendelse.eksterneVarsler.isNotEmpty() || hendelse.tilstand == AVLYST) {
            database.transaction {
                executeUpdate("""
                    update ekstern_varsel_kontaktinfo
                    set state = '${EksterntVarselTilstand.KANSELLERT}'
                    where notifikasjon_id = ?
                    and state = '${EksterntVarselTilstand.NY}'
                """) {
                    uuid(hendelse.notifikasjonId)
                }

                insertVarsler(
                    notifikasjonsId = hendelse.notifikasjonId,
                    varsler = hendelse.eksterneVarsler,
                    produsentId = hendelse.produsentId,
                    notifikasjonOpprettet = hendelse.oppdatertTidspunkt.atOffset(ZoneOffset.UTC)
                ) { tx: Transaction ->
                    tx.executeUpdate(
                        """
                    insert into merkelapp_grupperingsid_notifikasjon (merkelapp, grupperingsid, notifikasjon_id)
                    values (?, ?, ?)
                    on conflict do nothing
                    """
                    ) {
                        text(hendelse.merkelapp)
                        nullableText(hendelse.grupperingsid)
                        uuid(hendelse.notifikasjonId)
                    }
                }
            }
        }
    }

    private suspend fun oppdaterModellEtterPåminnelseOpprettet(påminnelseOpprettet: PåminnelseOpprettet) {
        /* Rewrite to batch insert? */
        database.transaction {
            insertVarsler(notifikasjonsId = påminnelseOpprettet.notifikasjonId,
                varsler = påminnelseOpprettet.eksterneVarsler,
                produsentId = påminnelseOpprettet.produsentId,
                notifikasjonOpprettet = påminnelseOpprettet.opprettetTidpunkt.atOffset(ZoneOffset.UTC),
                callback = {})
        }
    }

    private suspend fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: EksterntVarselFeilet) {
        oppdaterUtfall(eksterntVarselFeilet.varselId, SendeStatus.FEIL, eksterntVarselFeilet.råRespons)
    }

    private suspend fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: EksterntVarselVellykket) {
        oppdaterUtfall(eksterntVarselVellykket.varselId, SendeStatus.OK, eksterntVarselVellykket.råRespons)
    }

    private suspend fun oppdaterModellEtterEksterntVarselKansellert(hendelse: EksterntVarselKansellert) {
        database.nonTransactionalExecuteUpdate(
            """
            update ekstern_varsel_kontaktinfo 
            set 
                state = '${EksterntVarselTilstand.KANSELLERT}'
            where
                varsel_id = ? 
        """
        ) {
            uuid(hendelse.varselId)
        }
    }

    private suspend fun oppdaterUtfall(varselId: UUID, sendeStatus: SendeStatus, råRespons: JsonNode) {
        database.nonTransactionalExecuteUpdate(
            """
            update ekstern_varsel_kontaktinfo 
            set 
                altinn_response = ?::jsonb,
                state = '${EksterntVarselTilstand.KVITTERT}',
                sende_status = ?::status
            where
                varsel_id = ? 
        """
        ) {
            jsonb(råRespons)
            text(sendeStatus.toString())
            uuid(varselId)
        }
    }

    private suspend fun oppdaterModellEtterHardDelete(hardDelete: HardDelete) {
        database.transaction {
            if (hardDelete.merkelapp != null && hardDelete.grupperingsid != null) {
                executeUpdate(
                    """
                    insert into hard_delete (notifikasjon_id) 
                    (
                        select notifikasjon_id from merkelapp_grupperingsid_notifikasjon 
                        where merkelapp = ? and grupperingsid = ?
                    )
                    on conflict do nothing
                    """
                ) {
                    nullableText(hardDelete.merkelapp)
                    nullableText(hardDelete.grupperingsid)
                }

                executeUpdate(
                    """
                    delete from merkelapp_grupperingsid_notifikasjon 
                    where merkelapp = ? and grupperingsid = ?
                    """
                ) {
                    text(hardDelete.merkelapp)
                    text(hardDelete.grupperingsid)
                }
            }

            executeUpdate(
                """
                insert into hard_delete (notifikasjon_id) values (?)
                on conflict do nothing
                """
            ) {
                uuid(hardDelete.aggregateId)
            }

            executeUpdate(
                """
               delete from merkelapp_grupperingsid_notifikasjon where notifikasjon_id = ?  
                """
            ) {
                uuid(hardDelete.aggregateId)
            }
        }
    }

    suspend fun deleteScheduledHardDeletes() {
        database.nonTransactionalExecuteUpdate(
            """
            delete from ekstern_varsel_kontaktinfo evk
            using hard_delete hd
            where evk.state = '${EksterntVarselTilstand.KVITTERT}'
            and evk.notifikasjon_id = hd.notifikasjon_id;
            """
        )
    }

    private fun Transaction.insertVarsler(
        notifikasjonsId: UUID,
        varsler: List<EksterntVarsel>,
        produsentId: String,
        notifikasjonOpprettet: OffsetDateTime,
        callback: (tx: Transaction) -> Unit
    ) {
        if (isHardDeleted(notifikasjonsId)) {
            return
        }
        for (varsel in varsler) {
            putOnJobQueue(varsel.varselId)
            when (varsel) {
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

                is AltinnressursVarselKontaktinfo -> insertAltinnressursVarsel(
                    varsel = varsel,
                    produsentId = produsentId,
                    notifikasjonsId = notifikasjonsId,
                    notifikasjonOpprettet = notifikasjonOpprettet,
                )
            }
        }

        callback(this)
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
        executeUpdate(
            """
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
        """
        ) {
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
        executeUpdate(
            """
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
        """
        ) {
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
        executeUpdate(
            """
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
        """
        ) {
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

    private fun Transaction.insertAltinnressursVarsel(
        varsel: AltinnressursVarselKontaktinfo,
        notifikasjonsId: UUID,
        produsentId: String,
        notifikasjonOpprettet: OffsetDateTime,
    ) {
        executeUpdate(
            """
            INSERT INTO ekstern_varsel_kontaktinfo
            (
                varsel_id,
                notifikasjon_id,
                notifikasjon_opprettet,
                produsent_id,
                varsel_type,
                ressursid,
                fnr_eller_orgnr,
                ressurs_eposttittel,
                ressurs_epostinnhold,
                ressurs_smsinnhold,
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
                'ALTINNRESSURS',
                ?, /* ressursid */
                ?, /* fnr_eller_orgnr */
                ?, /* ressurs_eposttittel */
                ?, /* ressurs_epostinnhold */
                ?, /* ressurs_smsinnhold */
                ?, /* sendevindu */
                ?, /* sendetidspunkt */
                'NY' /* tilstand */
            )
            ON CONFLICT DO NOTHING;
        """
        ) {
            uuid(varsel.varselId)
            uuid(notifikasjonsId)
            timestamp_without_timezone_utc(notifikasjonOpprettet)
            text(produsentId)
            text(varsel.ressursId)
            text(varsel.virksomhetsnummer)
            text(varsel.epostTittel)
            text(varsel.epostHtmlBody)
            text(varsel.smsTekst)
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
        return database.nonTransactionalExecuteQuery(
            """
            UPDATE job_queue
            SET locked = false
            WHERE locked = true AND locked_until < CURRENT_TIMESTAMP
            RETURNING varsel_id, locked_by, locked_at
        """
        ) {
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
        database.nonTransactionalExecuteUpdate(
            """
            insert into job_queue (varsel_id, locked)
            (
                select varsel_id, false as locked from ekstern_varsel_kontaktinfo
                where 
                    state in ('${EksterntVarselTilstand.NY}', '${EksterntVarselTilstand.SENDT}')
                    and varsel_id not in (select varsel_id from job_queue)
            )
        """
        )
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
                            ordreId = getString("altinn_order_id"),
                        )

                        "EMAIL" -> EksternVarsel.Epost(
                            fnrEllerOrgnr = getString("fnr_eller_orgnr"),
                            sendeVindu = EksterntVarselSendingsvindu.valueOf(getString("sendevindu")),
                            sendeTidspunkt = getString("sendetidspunkt")?.let { LocalDateTime.parse(it) },
                            epostadresse = getString("epost_adresse"),
                            tittel = getString("tittel"),
                            body = getString("html_body"),
                            ordreId = getString("altinn_order_id"),
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

                        "ALTINNRESSURS" -> EksternVarsel.Altinnressurs(
                            fnrEllerOrgnr = getString("fnr_eller_orgnr"),
                            sendeVindu = EksterntVarselSendingsvindu.valueOf(getString("sendevindu")),
                            sendeTidspunkt = getString("sendetidspunkt")?.let { LocalDateTime.parse(it) },
                            resourceId = getString("ressursid"),
                            epostTittel = getString("ressurs_eposttittel"),
                            epostInnhold = getString("ressurs_epostinnhold"),
                            smsInnhold = getString("ressurs_smsinnhold"),
                            ordreId = getString("altinn_order_id")
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

                    EksterntVarselTilstand.KANSELLERT.toString() ->
                        EksternVarselTilstand.Kansellert(data)

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
        executeUpdate(
            """
            DELETE FROM job_queue WHERE varsel_id = ?
        """
        ) {
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
            executeUpdate(
                """ 
                update ekstern_varsel_kontaktinfo
                set 
                    state = '${EksterntVarselTilstand.SENDT}',
                    altinn_response = ?::jsonb,
                    sende_status = ?::status,
                    feilmelding = ?,
                    altinn_feilkode = ?
                where varsel_id = ?
            """
            ) {
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

    suspend fun markerSomSendtAndReleaseJob(varselId: UUID, response: Altinn3VarselKlient.OrderResponse) {
        database.transaction {
            executeUpdate(
                """ 
                update ekstern_varsel_kontaktinfo
                set 
                    state = '${EksterntVarselTilstand.SENDT}',
                    altinn_order_id = ?,
                    altinn_response = ?::jsonb,
                    sende_status = ?::status,
                    feilmelding = ?,
                    altinn_feilkode = ?
                where varsel_id = ?
            """
            ) {
                nullableText(when (response) {
                    is Altinn3VarselKlient.OrderResponse.Success -> response.orderId
                    is Altinn3VarselKlient.ErrorResponse -> null
                })
                jsonb(response.rå)
                when (response) {
                    is Altinn3VarselKlient.ErrorResponse -> {
                        text("FEIL")
                        nullableText(response.message)
                        nullableText(response.code)
                    }
                    is Altinn3VarselKlient.OrderResponse.Success -> {
                        text("OK")
                        nullableText(null)
                        nullableText(null)
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
        return database.nonTransactionalExecuteQuery(
            """
            select count(*) as count from job_queue 
        """
        ) {
            this.getInt("count")
        }.first()
    }

    suspend fun waitQueueCount(): Pair<Int, Int> {
        return database.nonTransactionalExecuteQuery(
            """
            select
                count(case when resume_job_at <= now() then 1 end) as past,
                count(case when resume_job_at > now() then 1 end) as future
            from wait_queue
        """
        ) {
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
        database.nonTransactionalExecuteUpdate(
            """
            insert into emergency_break (id, stop_processing, detected_at)
            values (0, ?, CURRENT_TIMESTAMP)
            on conflict (id) do update
                set 
                    stop_processing = ?,
                    detected_at = CURRENT_TIMESTAMP
        """
        ) {
            boolean(newState)
            boolean(newState)
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
