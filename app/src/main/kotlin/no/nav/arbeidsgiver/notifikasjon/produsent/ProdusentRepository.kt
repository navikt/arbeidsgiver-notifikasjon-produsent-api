package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.hendelse.HardDeletedRepository
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import java.lang.RuntimeException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class ProdusentRepository(
    private val database: Database,
) : HardDeletedRepository(database) {
    enum class AggregateType {
        SAK,
        BESKJED,
        OPPGAVE,
    }

    val log = logger()

    suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon? =
        hentNotifikasjonerMedVarsler(
            """ 
                where id = ?
            """
        ) {
            uuid(id)
        }
            .firstOrNull()

    suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? =
        hentNotifikasjonerMedVarsler(
            """ 
                where
                    ekstern_id = ? and 
                    merkelapp = ?
            """
        ) {
            text(eksternId)
            text(merkelapp)
        }
            .firstOrNull()

    suspend fun finnNotifikasjoner(
        merkelapper: List<String>,
        grupperingsid: String?,
        antall: Int,
        offset: Int,
    ): List<ProdusentModel.Notifikasjon> =
        hentNotifikasjonerMedVarsler(
            """ 
                where 
                    merkelapp = any(?)
                    ${grupperingsid?.let { "and grupperingsid = ?" } ?: ""} 
                limit ?
                offset ?
            """
        ) {
            textArray(merkelapper)
            grupperingsid?.let { text(grupperingsid) }
            integer(antall)
            integer(offset)
        }

    suspend fun hentSak(grupperingsid: String, merkelapp: String): ProdusentModel.Sak? {
        return hentSaker(
            where = """
               grupperingsid = ? and merkelapp = ?
            """,
            variables = {
                text(grupperingsid)
                text(merkelapp)
            }
        )
            .firstOrNull()
    }

    suspend fun hentSak(id: UUID): ProdusentModel.Sak? {
        return hentSaker(
            where = """
                id = ?
            """,
            variables = {
                uuid(id)
            }
        )
            .firstOrNull()
    }

    suspend fun erHardDeleted(type: AggregateType, merkelapp: String, grupperingsid: String) =
        database.nonTransactionalExecuteQuery("""
            select * from hard_deleted_aggregates_metadata where aggregate_type = ? and merkelapp = ? and grupperingsid = ?
            """,
            {
                text(type.name)
                text(merkelapp)
                text(grupperingsid)
            }
        ) {}.isNotEmpty()

    private suspend fun hentSaker(
        where: String,
        variables: ParameterSetters.() -> Unit,
    ): List<ProdusentModel.Sak> {
        return database.nonTransactionalExecuteQuery(
            """ 
            with 
                valgt_sak as (
                    select sak.* 
                    from sak
                    where $where
                )
            select 
                valgt_sak.*, 
                coalesce(statusoppdateringer_json.statusoppdateringer::jsonb, '[]'::jsonb) as statusoppdateringer
            from valgt_sak
            left join statusoppdateringer_json
                on statusoppdateringer_json.sak_id = valgt_sak.id
            """,
            variables
        ) {
            ProdusentModel.Sak(
                merkelapp = getString("merkelapp"),
                tittel = getString("tittel"),
                grupperingsid = getString("grupperingsid"),
                lenke = getString("lenke"),
                mottakere = laxObjectMapper.readValue(getString("mottakere")),
                opprettetTidspunkt = getObject("tidspunkt_mottatt", OffsetDateTime::class.java),
                id = getObject("id", UUID::class.java),
                deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                virksomhetsnummer = getString("virksomhetsnummer"),
                statusoppdateringer = laxObjectMapper.readValue(getString("statusoppdateringer"))
            )
        }
    }

    private suspend fun hentNotifikasjonerMedVarsler(
        filter: String,
        setup: ParameterSetters.() -> Unit
    ): List<ProdusentModel.Notifikasjon> =
        database.nonTransactionalExecuteQuery(
            """ 
            with 
                valgt_notifikasjon as (
                    select notifikasjon.* 
                    from notifikasjon
                    $filter
                )
            select 
                valgt_notifikasjon.*, 
                coalesce(ev.eksterne_varsler_json, '[]'::json) as eksterne_varsler,
                coalesce(pev.paaminnelse_eksterne_varsler_json, '[]'::json) as paaminnelse_eksterne_varsler,
                (coalesce(ma.mottakere::jsonb, '[]'::jsonb)  || coalesce(md.mottakere::jsonb, '[]'::jsonb)) as mottakere
            from valgt_notifikasjon
            left join eksterne_varsler_json ev 
                on ev.notifikasjon_id = valgt_notifikasjon.id
            left join paaminnelse_eksterne_varsler_json pev 
                on pev.notifikasjon_id = valgt_notifikasjon.id
            left join mottakere_altinn_enkeltrettighet_json ma
                on ma.notifikasjon_id = valgt_notifikasjon.id
            left join mottakere_digisyfo_json md
                on md.notifikasjon_id = valgt_notifikasjon.id
            """,
            setup
        ) {
            when (val type = getString("type")) {
                "BESKJED" -> ProdusentModel.Beskjed(
                    merkelapp = getString("merkelapp"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottakere = laxObjectMapper.readValue(getString("mottakere")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                    eksterneVarsler = laxObjectMapper.readValue(getString("eksterne_varsler")),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                )

                "OPPGAVE" -> ProdusentModel.Oppgave(
                    merkelapp = getString("merkelapp"),
                    tilstand = ProdusentModel.Oppgave.Tilstand.valueOf(getString("tilstand")),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottakere = laxObjectMapper.readValue(getString("mottakere")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                    eksterneVarsler = laxObjectMapper.readValue(getString("eksterne_varsler")),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    frist = getObject("frist", LocalDate::class.java),
                    påminnelseEksterneVarsler = laxObjectMapper.readValue(getString("paaminnelse_eksterne_varsler")),
                )

                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        if (erHardDeleted(hendelse.aggregateId)) {
            log.info("skipping harddeleted event {}", hendelse)
            return
        }

        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored: Unit = when (hendelse) {
            is SakOpprettet -> oppdaterModellEtterSakOpprettet(hendelse)
            is NyStatusSak -> oppdaterModellEtterNyStatusSak(hendelse)
            is BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is OppgaveUtført -> oppdaterModellEtterOppgaveUtført(hendelse, metadata)
            is OppgaveUtgått -> oppdaterModellEtterOppgaveUtgått(hendelse)
            is PåminnelseOpprettet -> /* Ignorer */ Unit
            is BrukerKlikket -> /* Ignorer */ Unit
            is SoftDelete -> oppdaterModellEtterSoftDelete(hendelse)
            is HardDelete -> oppdaterModellEtterHardDelete(hendelse)
            is EksterntVarselVellykket -> oppdaterModellEtterEksterntVarselVellykket(hendelse)
            is EksterntVarselFeilet -> oppdaterModellEtterEksterntVarselFeilet(hendelse)
            is FristUtsatt -> oppdaterModellEtterFristUtsatt(hendelse)
        }
    }

    private suspend fun oppdaterModellEtterSakOpprettet(sakOpprettet: SakOpprettet) {
        database.transaction {
            executeUpdate(
                """
                    insert into sak(id, merkelapp, grupperingsid, virksomhetsnummer, mottakere, tittel, lenke, tidspunkt_mottatt)
                    values (?, ?, ?, ?, ?::jsonb, ?, ?, now())
                    on conflict do nothing
                """
            ) {
                uuid(sakOpprettet.sakId)
                text(sakOpprettet.merkelapp)
                text(sakOpprettet.grupperingsid)
                text(sakOpprettet.virksomhetsnummer)
                jsonb(sakOpprettet.mottakere)
                text(sakOpprettet.tittel)
                text(sakOpprettet.lenke)
            }.also {
                if (it == 0) {
                    // noop. saken finnes allerede
                } else {
                    executeUpdate(
                        """
                        insert into sak_id (incoming_sak_id, sak_id) values (?, ?)
                        on conflict do nothing
                    """
                    ) {
                        uuid(sakOpprettet.sakId)
                        uuid(sakOpprettet.sakId)
                    }
                }
            }
        }
    }

    private fun Transaction.finnDbSakId(sakId: UUID): UUID? =
        executeQuery(
            """
                    select sak_id from sak_id where incoming_sak_id = ?
                """,
            setup = { uuid(sakId) },
            transform = { getObject("sak_id", UUID::class.java) },
        ).firstOrNull()


    private suspend fun oppdaterModellEtterNyStatusSak(nyStatusSak: NyStatusSak) {
        database.transaction {
            val sakId = finnDbSakId(nyStatusSak.sakId) ?: return@transaction // log? metric?

            executeUpdate(
                """
                insert into sak_status
                (id, idempotence_key, sak_id, status, overstyr_statustekst_med, tidspunkt_oppgitt, tidspunkt_mottatt)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing;
            """
            ) {
                uuid(nyStatusSak.hendelseId)
                text(nyStatusSak.idempotensKey)
                uuid(sakId)
                text(nyStatusSak.status.name)
                nullableText(nyStatusSak.overstyrStatustekstMed)
                nullableTimestamptz(nyStatusSak.oppgittTidspunkt)
                timestamp_with_timezone(nyStatusSak.mottattTidspunkt)
            }
        }
    }


    private suspend fun oppdaterModellEtterHardDelete(hardDelete: HardDelete) {
        database.transaction {
            registrerHardDelete(this, hardDelete)
            executeQuery("""
                select aggregate_type from (
                    select 'SAK' as aggregate_type from sak where id = ?
                    union
                    select 'BESKJED' as aggregate_type from notifikasjon where id = ?
                    union
                    select 'OPPGAVE' as aggregate_type from notifikasjon where id = ?
                ) as aggregate_type
            """, setup = {
                uuid(hardDelete.aggregateId)
                uuid(hardDelete.aggregateId)
                uuid(hardDelete.aggregateId)
            }, transform = {
                AggregateType.valueOf(getString("aggregate_type"))
            }).firstOrNull()?.let {
                // har ikke grunnlag for å backfille metadata, men for fremtidige events vil vi ha metadata.
                // derfor er det en null-sjekk rundt dette
                executeUpdate(
                    """
                insert into hard_deleted_aggregates_metadata(aggregate_id, aggregate_type, merkelapp, grupperingsid)
                values (?, ?, ?, ?);
                """
                ) {
                    uuid(hardDelete.aggregateId)
                    text(it.name)
                    nullableText(hardDelete.merkelapp)
                    nullableText(hardDelete.grupperingsid)
                }
            }

            if (hardDelete.grupperingsid != null && hardDelete.merkelapp != null) {
                // cascade hard delete av sak med grupperingsid og merkelapp
                executeUpdate("""delete from notifikasjon n where n.grupperingsid = ? and merkelapp = ?;""") {
                    text(hardDelete.grupperingsid)
                    text(hardDelete.merkelapp)
                }
            }
            executeUpdate(
                """
                delete from notifikasjon
                where id = ?
                """
            ) {
                uuid(hardDelete.aggregateId)
            }
            executeUpdate(
                """
                delete from sak
                where id = ?
                """
            ) {
                uuid(hardDelete.aggregateId)
            }
        }
    }

    private suspend fun oppdaterModellEtterSoftDelete(softDelete: SoftDelete) {
        database.transaction {
            executeUpdate(
                """
                UPDATE notifikasjon
                SET deleted_at = ?
                WHERE id = ?
                """
            ) {
                timestamp_with_timezone(softDelete.deletedAt)
                uuid(softDelete.aggregateId)
            }
            executeUpdate(
                """
                UPDATE sak
                SET deleted_at = ?
                WHERE id = ?
                """
            ) {
                timestamp_with_timezone(softDelete.deletedAt)
                uuid(softDelete.aggregateId)
            }
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtført(utførtHendelse: OppgaveUtført, metadata: HendelseMetadata) {
        database.nonTransactionalExecuteUpdate(
            """
            UPDATE notifikasjon
            SET tilstand = '${ProdusentModel.Oppgave.Tilstand.UTFOERT}',
            utfoert_tidspunkt = ?
            WHERE id = ?
        """
        ) {
            offsetDateTimeAsText(utførtHendelse.utfoertTidspunkt ?: metadata.timestamp.atOffset(ZoneOffset.UTC))
            uuid(utførtHendelse.notifikasjonId)
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtgått(utgåttHendelse: OppgaveUtgått) {
        database.nonTransactionalExecuteUpdate(
            """
            UPDATE notifikasjon
            SET tilstand = '${ProdusentModel.Oppgave.Tilstand.UTGAATT}',
            utgaatt_tidspunkt = ?
            WHERE id = ?
        """
        ) {
            offsetDateTimeAsText(utgåttHendelse.utgaattTidspunkt)
            uuid(utgåttHendelse.notifikasjonId)
        }
    }

    private suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: BeskjedOpprettet) {
        database.transaction {
            executeUpdate(
                """
                insert into notifikasjon(
                    type,
                    tilstand,
                    id,
                    merkelapp,
                    tekst,
                    grupperingsid,
                    lenke,
                    ekstern_id,
                    opprettet_tidspunkt,
                    virksomhetsnummer
                )
                values ('BESKJED', 'NY', ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing;
            """
            ) {
                uuid(beskjedOpprettet.notifikasjonId)
                text(beskjedOpprettet.merkelapp)
                text(beskjedOpprettet.tekst)
                nullableText(beskjedOpprettet.grupperingsid)
                text(beskjedOpprettet.lenke)
                text(beskjedOpprettet.eksternId)
                timestamp_with_timezone(beskjedOpprettet.opprettetTidspunkt)
                text(beskjedOpprettet.virksomhetsnummer)
            }

            for (mottaker in beskjedOpprettet.mottakere) {
                storeMottaker(beskjedOpprettet.notifikasjonId, mottaker)
            }

            executeBatch(
                """
                insert into eksternt_varsel(
                    varsel_id,
                    notifikasjon_id,
                    status
                )
                values (?, ?, 'NY')
                on conflict do nothing;
                """,
                beskjedOpprettet.eksterneVarsler
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(beskjedOpprettet.notifikasjonId)
            }
        }
    }

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: OppgaveOpprettet) {
        database.transaction {
            executeUpdate(
                """
                insert into notifikasjon(
                    type,
                    tilstand,
                    id,
                    merkelapp,
                    tekst,
                    grupperingsid,
                    lenke,
                    ekstern_id,
                    opprettet_tidspunkt,
                    virksomhetsnummer,
                    frist
                )
                values ('OPPGAVE', 'NY', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing;
            """
            ) {
                uuid(oppgaveOpprettet.notifikasjonId)
                text(oppgaveOpprettet.merkelapp)
                text(oppgaveOpprettet.tekst)
                nullableText(oppgaveOpprettet.grupperingsid)
                text(oppgaveOpprettet.lenke)
                text(oppgaveOpprettet.eksternId)
                timestamp_with_timezone(oppgaveOpprettet.opprettetTidspunkt)
                text(oppgaveOpprettet.virksomhetsnummer)
                nullableDate(oppgaveOpprettet.frist)
            }

            for (mottaker in oppgaveOpprettet.mottakere) {
                storeMottaker(oppgaveOpprettet.notifikasjonId, mottaker)
            }

            executeBatch(
                """
                insert into eksternt_varsel(
                    varsel_id,
                    notifikasjon_id,
                    status
                )
                values (?, ?, 'NY')
                on conflict do nothing;
                """,
                oppgaveOpprettet.eksterneVarsler
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(oppgaveOpprettet.notifikasjonId)
            }

            executeBatch(
                """
                insert into paaminnelse_eksternt_varsel(
                    varsel_id,
                    notifikasjon_id,
                    status
                )
                values (?, ?, 'NY')
                on conflict do nothing;
                """,
                oppgaveOpprettet.påminnelse?.eksterneVarsler.orEmpty()
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(oppgaveOpprettet.notifikasjonId)
            }
        }
    }

    private suspend fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: EksterntVarselVellykket) {
        database.nonTransactionalExecuteUpdate(
            """
            update eksternt_varsel set status = 'SENDT' where varsel_id = ?
            """
        ) {
            uuid(eksterntVarselVellykket.varselId)
        }
        database.nonTransactionalExecuteUpdate(
            """
            update paaminnelse_eksternt_varsel set status = 'SENDT' where varsel_id = ?
            """
        ) {
            uuid(eksterntVarselVellykket.varselId)
        }
    }

    private suspend fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: EksterntVarselFeilet) {
        database.nonTransactionalExecuteUpdate(
            """
            update eksternt_varsel 
            set status = 'FEILET',
                feilmelding = ?  
            where varsel_id = ?
            """
        ) {
            text(eksterntVarselFeilet.feilmelding)
            uuid(eksterntVarselFeilet.varselId)
        }
        database.nonTransactionalExecuteUpdate(
            """
            update paaminnelse_eksternt_varsel 
            set status = 'FEILET',
                feilmelding = ?  
            where varsel_id = ?
            """
        ) {
            text(eksterntVarselFeilet.feilmelding)
            uuid(eksterntVarselFeilet.varselId)
        }
    }

    private suspend fun oppdaterModellEtterFristUtsatt(fristUtsatt: FristUtsatt) {
        database.transaction {
            executeUpdate(
                """
                UPDATE notifikasjon
                SET tilstand = '${ProdusentModel.Oppgave.Tilstand.NY}',
                utgaatt_tidspunkt = null,
                frist = ?
                WHERE id = ?
                """
            ) {
                date(fristUtsatt.frist)
                uuid(fristUtsatt.notifikasjonId)
            }

            executeBatch(
                """
                insert into paaminnelse_eksternt_varsel(
                    varsel_id,
                    notifikasjon_id,
                    status
                )
                values (?, ?, 'NY')
                on conflict do nothing;
                """,
                fristUtsatt.påminnelse?.eksterneVarsler.orEmpty()
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(fristUtsatt.notifikasjonId)
            }
        }
    }

    private fun Transaction.storeMottaker(notifikasjonId: UUID, mottaker: Mottaker) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored = when (mottaker) {
            is NærmesteLederMottaker -> storeNærmesteLederMottaker(notifikasjonId, mottaker)
            is AltinnMottaker -> storeAltinnMottaker(notifikasjonId, mottaker)
            is HendelseModel._AltinnRolleMottaker -> basedOnEnv(
                prod = { throw RuntimeException("AltinnRolleMottaker støttes ikke i prod") },
                other = { },
            )

            is HendelseModel._AltinnReporteeMottaker -> basedOnEnv(
                prod = { throw RuntimeException("AltinnReporteeMottaker støttes ikke i prod") },
                other = { },
            )
        }
    }

    private fun Transaction.storeNærmesteLederMottaker(notifikasjonId: UUID, mottaker: NærmesteLederMottaker) {
        executeUpdate(
            """
            insert into mottaker_digisyfo(notifikasjon_id, virksomhet, fnr_leder, fnr_sykmeldt)
            values (?, ?, ?, ?)
            on conflict do nothing
        """
        ) {
            uuid(notifikasjonId)
            text(mottaker.virksomhetsnummer)
            text(mottaker.naermesteLederFnr)
            text(mottaker.ansattFnr)
        }
    }

    private fun Transaction.storeAltinnMottaker(notifikasjonId: UUID, mottaker: AltinnMottaker) {
        executeUpdate(
            """
            insert into mottaker_altinn_enkeltrettighet
                (notifikasjon_id, virksomhet, service_code, service_edition)
            values (?, ?, ?, ?)
            on conflict do nothing
        """
        ) {
            uuid(notifikasjonId)
            text(mottaker.virksomhetsnummer)
            text(mottaker.serviceCode)
            text(mottaker.serviceEdition)
        }
    }
}