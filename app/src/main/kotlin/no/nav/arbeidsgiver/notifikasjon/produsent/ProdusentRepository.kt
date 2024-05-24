package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.hendelse.HardDeletedRepository
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselKansellert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.FristUtsatt
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOppdatert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository.AggregateType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

interface ProdusentRepository {
    enum class AggregateType {
        SAK,
        BESKJED,
        OPPGAVE,
    }

    suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon?

    suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon?

    suspend fun finnNotifikasjoner(
        merkelapper: List<String>,
        grupperingsid: String?,
        antall: Int,
        offset: Int,
    ): ResultsWrapper<ProdusentModel.Notifikasjon>

    suspend fun hentSak(grupperingsid: String, merkelapp: String): ProdusentModel.Sak?

    suspend fun hentSak(id: UUID): ProdusentModel.Sak?

    suspend fun erHardDeleted(type: AggregateType, merkelapp: String, grupperingsid: String): Boolean

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata)
    suspend fun notifikasjonOppdateringFinnes(id: UUID, idempotenceKey: String): Boolean
}

data class ResultsWrapper<T>(
    val results: List<T>,
    val hasMore: Boolean,
)

class ProdusentRepositoryImpl(
    private val database: Database,
) : HardDeletedRepository(database), ProdusentRepository {

    val log = logger()

    override suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon? =
        hentNotifikasjonerMedVarsler(
            """ 
                where id = ?
            """
        ) {
            uuid(id)
        }
            .firstOrNull()

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? =
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

    override suspend fun finnNotifikasjoner(
        merkelapper: List<String>,
        grupperingsid: String?,
        antall: Int,
        offset: Int,
    ): ResultsWrapper<ProdusentModel.Notifikasjon> {
        val notifikasjoner = hentNotifikasjonerMedVarsler(
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
            integer(antall + 1)
            integer(offset)
        }

        val hasMore = notifikasjoner.size > antall
        return ResultsWrapper(
            results = if (hasMore) notifikasjoner.dropLast(1) else notifikasjoner,
            hasMore = hasMore
        )
    }

    override suspend fun hentSak(grupperingsid: String, merkelapp: String): ProdusentModel.Sak? {
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

    override suspend fun hentSak(id: UUID): ProdusentModel.Sak? {
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

    override suspend fun erHardDeleted(type: AggregateType, merkelapp: String, grupperingsid: String) =
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
                valgt_sak as materialized (
                    select sak.* 
                    from sak
                    where $where
                )
            select 
                valgt_sak.*, 
                coalesce(
                    (select statusoppdateringer::jsonb from statusoppdateringer_json where sak_id = valgt_sak.id),
                    '[]'::jsonb
                ) as statusoppdateringer
            from valgt_sak
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
                coalesce(
                    (
                        select ev.eksterne_varsler_json
                        from eksterne_varsler_json ev
                        where ev.notifikasjon_id = valgt_notifikasjon.id
                    ),
                    '[]'::json
                ) as eksterne_varsler,
                coalesce(
                    (
                        select pev.paaminnelse_eksterne_varsler_json
                        from paaminnelse_eksterne_varsler_json pev
                        where pev.notifikasjon_id = valgt_notifikasjon.id
                    ),
                    '[]'::json
                ) as paaminnelse_eksterne_varsler,
                (coalesce(
                    (
                        select ma.mottakere::jsonb
                        from mottakere_altinn_enkeltrettighet_json ma
                        where ma.notifikasjon_id = valgt_notifikasjon.id
                    ),
                    '[]'::jsonb)
                || coalesce(
                    (
                        select md.mottakere::jsonb
                        from mottakere_digisyfo_json md
                        where md.notifikasjon_id = valgt_notifikasjon.id
                    ),
                    '[]'::jsonb)
                ) as mottakere
            from valgt_notifikasjon
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

                "KALENDERAVTALE" -> ProdusentModel.Kalenderavtale(
                    merkelapp = getString("merkelapp"),
                    tilstand = ProdusentModel.Kalenderavtale.Tilstand.valueOf(getString("tilstand")),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottakere = laxObjectMapper.readValue(getString("mottakere")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                    eksterneVarsler = laxObjectMapper.readValue(getString("eksterne_varsler")),
                    påminnelseEksterneVarsler = laxObjectMapper.readValue(getString("paaminnelse_eksterne_varsler")),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    startTidspunkt = getString("start_tidspunkt").let { LocalDateTime.parse(it) },
                    sluttTidspunkt = getString("slutt_tidspunkt")?.let { LocalDateTime.parse(it) },
                    lokasjon = getString("lokasjon")?.let {
                        laxObjectMapper.readValue(it)
                    },
                    digitalt = getBoolean("digitalt"),
                )

                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        if (erHardDeleted(hendelse.aggregateId)) {
            log.info("skipping harddeleted event {}", hendelse)
            return
        }

        when (hendelse) {
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
            is EksterntVarselKansellert -> oppdaterModellEtterEksterntVarselKansellert(hendelse)
            is FristUtsatt -> oppdaterModellEtterFristUtsatt(hendelse)
            is KalenderavtaleOpprettet -> oppdaterModellEtterKalenderavtaleOpprettet(hendelse)
            is KalenderavtaleOppdatert -> oppdaterModellEtterKalenderavtaleOppdatert(hendelse)
        }
    }

    override suspend fun notifikasjonOppdateringFinnes(id: UUID, idempotenceKey: String): Boolean =
        database.nonTransactionalExecuteQuery(
            """
            select * from notifikasjon_oppdatering where notifikasjon_id = ? and idempotence_key = ?
            """,
            {
                uuid(id)
                text(idempotenceKey)
            }
        ) {}.isNotEmpty()

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
                nullableText(sakOpprettet.lenke)
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
            executeUpdate(
                """
                delete from eksternt_varsel
                where notifikasjon_id = ?
                """
            ) {
                uuid(hardDelete.aggregateId)
            }
            executeUpdate(
                """
                delete from paaminnelse_eksternt_varsel
                where notifikasjon_id = ?
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
                    status,
                    kilde_hendelse
                )
                values (?, ?, 'NY', ?::jsonb)
                on conflict(varsel_id) do update
                set kilde_hendelse = excluded.kilde_hendelse;
                """,
                beskjedOpprettet.eksterneVarsler
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(beskjedOpprettet.notifikasjonId)
                jsonb(eksterntVarsel)
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
                    status,
                    kilde_hendelse
                )
                values (?, ?, 'NY', ?::jsonb)
                on conflict(varsel_id) do update
                set kilde_hendelse = excluded.kilde_hendelse;
                """,
                oppgaveOpprettet.eksterneVarsler
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(oppgaveOpprettet.notifikasjonId)
                jsonb(eksterntVarsel)
            }

            executeBatch(
                """
                insert into paaminnelse_eksternt_varsel(
                    varsel_id,
                    notifikasjon_id,
                    status,
                    kilde_hendelse
                )
                values (?, ?, 'NY', ?::jsonb)
                on conflict(varsel_id) do update 
                set kilde_hendelse = excluded.kilde_hendelse;
                """,
                oppgaveOpprettet.påminnelse?.eksterneVarsler.orEmpty()
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(oppgaveOpprettet.notifikasjonId)
                jsonb(eksterntVarsel)
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

    private suspend fun oppdaterModellEtterEksterntVarselKansellert(eksterntVarselKansellert: EksterntVarselKansellert) {
        database.nonTransactionalExecuteUpdate(
            """
            update eksternt_varsel 
            set status = 'KANSELLERT'
            where varsel_id = ?
            """
        ) {
            uuid(eksterntVarselKansellert.varselId)
        }
        database.nonTransactionalExecuteUpdate(
            """
            update paaminnelse_eksternt_varsel 
            set status = 'KANSELLERT'
            where varsel_id = ?
            """
        ) {
            uuid(eksterntVarselKansellert.varselId)
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
                    status,
                    kilde_hendelse
                )
                values (?, ?, 'NY', ?)
                on conflict(varsel_id) do update
                set kilde_hendelse = excluded.kilde_hendelse;;
                """,
                fristUtsatt.påminnelse?.eksterneVarsler.orEmpty()
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(fristUtsatt.notifikasjonId)
                jsonb(eksterntVarsel)
            }
        }
    }

    private fun Transaction.storeMottaker(notifikasjonId: UUID, mottaker: Mottaker) {
        when (mottaker) {
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

    private suspend fun oppdaterModellEtterKalenderavtaleOpprettet(hendelse: KalenderavtaleOpprettet) {
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
                    start_tidspunkt,
                    slutt_tidspunkt,
                    lokasjon,
                    digitalt
                )
                values ('KALENDERAVTALE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                on conflict do nothing;
            """
            ) {
                with(hendelse) {
                    text(tilstand.name)
                    uuid(notifikasjonId)
                    text(merkelapp)
                    text(tekst)
                    nullableText(grupperingsid)
                    text(lenke)
                    text(eksternId)
                    timestamp_with_timezone(opprettetTidspunkt)
                    text(virksomhetsnummer)

                    localDateTimeAsText(startTidspunkt)
                    nullableLocalDateTimeAsText(sluttTidspunkt)
                    nullableJsonb(lokasjon)
                    boolean(erDigitalt)
                }
            }

            for (mottaker in hendelse.mottakere) {
                storeMottaker(hendelse.notifikasjonId, mottaker)
            }

            executeBatch(
                """
                insert into eksternt_varsel(
                    varsel_id,
                    notifikasjon_id,
                    status,
                    kilde_hendelse
                )
                values (?, ?, 'NY', ?::jsonb)
                on conflict(varsel_id) do update
                set kilde_hendelse = excluded.kilde_hendelse;
                """,
                hendelse.eksterneVarsler
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(hendelse.notifikasjonId)
                jsonb(eksterntVarsel)
            }

            executeBatch(
                """
                insert into paaminnelse_eksternt_varsel(
                    varsel_id,
                    notifikasjon_id,
                    status,
                    kilde_hendelse
                )
                values (?, ?, 'NY', ?::jsonb)
                on conflict(varsel_id) do update 
                set kilde_hendelse = excluded.kilde_hendelse;
                """,
                hendelse.påminnelse?.eksterneVarsler.orEmpty()
            ) { eksterntVarsel ->
                uuid(eksterntVarsel.varselId)
                uuid(hendelse.notifikasjonId)
                jsonb(eksterntVarsel)
            }
        }
    }

    private suspend fun oppdaterModellEtterKalenderavtaleOppdatert(hendelse: KalenderavtaleOppdatert) {
        database.transaction {
            executeUpdate(
                """
                update notifikasjon set 
                    tilstand = coalesce(?, tilstand),
                    tekst = coalesce(?, tekst),
                    lenke = coalesce(?, lenke),
                    start_tidspunkt = coalesce(?, start_tidspunkt),
                    slutt_tidspunkt = coalesce(?, slutt_tidspunkt),
                    lokasjon = coalesce(?::jsonb, lokasjon),
                    digitalt = coalesce(?, digitalt)
                where id = ?;
            """
            ) {
                with(hendelse) {

                    nullableText(tilstand?.name)
                    nullableText(tekst)
                    nullableText(lenke)
                    nullableLocalDateTimeAsText(startTidspunkt)
                    nullableLocalDateTimeAsText(sluttTidspunkt)
                    nullableJsonb(lokasjon)
                    nullableBoolean(erDigitalt)

                    uuid(notifikasjonId)
                }
            }
            if (hendelse.idempotenceKey != null) {
                executeUpdate(
                    """
                    insert into notifikasjon_oppdatering(
                        hendelse_id, notifikasjon_id, idempotence_key
                    ) values (?, ?, ?) on conflict(hendelse_id) do nothing;
                """
                ) {
                    uuid(hendelse.hendelseId)
                    uuid(hendelse.notifikasjonId)
                    text(hendelse.idempotenceKey)
                }
            }
        }
    }
}