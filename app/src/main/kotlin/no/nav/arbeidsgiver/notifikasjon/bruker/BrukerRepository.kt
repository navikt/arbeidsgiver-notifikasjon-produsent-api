package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
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
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

interface BrukerRepository {
    suspend fun hentNotifikasjoner(
        fnr: String,
        tilganger: Tilganger,
    ): List<BrukerModel.Notifikasjon>

    class HentSakerResultat(
        val totaltAntallSaker: Int,
        val saker: List<BrukerModel.Sak>,
        val sakstyper: List<Sakstype>,
        val oppgaveTilstanderMedAntall: List<Oppgavetilstand>,
    )

    class Oppgavetilstand(
        val navn: BrukerModel.Oppgave.Tilstand,
        val antall: Int,
    )

    class Sakstype(
        val navn: String,
        val antall: Int,
    )

    suspend fun hentSaker(
        fnr: String,
        virksomhetsnummer: List<String>,
        tilganger: Tilganger,
        tekstsoek: String?,
        sakstyper: List<String>?,
        offset: Int,
        limit: Int,
        sortering: BrukerAPI.SakSortering,
        oppgaveTilstand: List<BrukerModel.Oppgave.Tilstand>?,
    ): HentSakerResultat

    /** Denne funksjonaliteten var tidligere en del av [hentSaker], men vi greide ikke
     * å overbevise postgres til å ikke gjøre en full merge-join med [sak_status]-tabellen,
     * og dette virker som en akse
     */
    suspend fun berikSaker(
        saker: List<BrukerModel.Sak>,
    ): Map<UUID, BrukerModel.Sakberikelse>

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata)
    suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String?
    suspend fun oppdaterModellEtterNærmesteLederLeesah(nærmesteLederLeesah: NarmesteLederLeesah)
    suspend fun hentSakstyper(fnr: String, tilganger: Tilganger): List<String>
    suspend fun hentSakerForNotifikasjoner(
        grupperinger: List<BrukerModel.Gruppering>
    ): Map<String, String>
}

class BrukerRepositoryImpl(
    private val database: Database
) : BrukerRepository, HardDeletedRepository(database) {
    private val log = logger()
    private val timer = Metrics.meterRegistry.timer("query_model_repository_hent_notifikasjoner")

    override suspend fun hentNotifikasjoner(
        fnr: String,
        tilganger: Tilganger,
    ): List<BrukerModel.Notifikasjon> = timer.coRecord {
        val tilgangerAltinnMottaker = tilganger.tjenestetilganger.map {
            AltinnMottaker(
                serviceCode = it.servicecode,
                serviceEdition = it.serviceedition,
                virksomhetsnummer = it.virksomhet
            )
        }

        database.nonTransactionalExecuteQuery(
            /*  quotes are necessary for fields from json, otherwise they are lower-cased */
            """
            with 
                mine_altinntilganger as (
                    select * from json_to_recordset(?::json) 
                    as (virksomhetsnummer text, "serviceCode" text, "serviceEdition" text)
                ),
                mine_altinn_notifikasjoner as (
                    select er.notifikasjon_id
                    from mottaker_altinn_enkeltrettighet er
                    join mine_altinntilganger at on 
                        er.virksomhet = at.virksomhetsnummer and
                        er.service_code = at."serviceCode" and
                        er.service_edition = at."serviceEdition"
                    where
                        er.notifikasjon_id is not null
                ),
                mine_digisyfo_notifikasjoner as (
                    select notifikasjon_id 
                    from mottaker_digisyfo_for_fnr
                    where fnr_leder = ? and notifikasjon_id is not null
                ),
                mine_notifikasjoner as (
                    (select * from mine_digisyfo_notifikasjoner)
                    union 
                    (select * from mine_altinn_notifikasjoner)
                )
            select 
                n.*, 
                klikk.notifikasjonsid is not null as klikketPaa
            from mine_notifikasjoner as mn
            join notifikasjon as n on n.id = mn.notifikasjon_id
            left outer join brukerklikk as klikk on
                klikk.notifikasjonsid = n.id
                and klikk.fnr = ?
            order by 
                coalesce(paaminnelse_tidspunkt, opprettet_tidspunkt, utfoert_tidspunkt) desc
            limit 200
            """,
            {
                jsonb(tilgangerAltinnMottaker)
                text(fnr)
                text(fnr)
            }
        ) {
            when (val type = getString("type")) {
                "BESKJED" -> BrukerModel.Beskjed(
                    merkelapp = getString("merkelapp"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getUuid("id"),
                    klikketPaa = getBoolean("klikketPaa")
                )
                "OPPGAVE" -> BrukerModel.Oppgave(
                    merkelapp = getString("merkelapp"),
                    tilstand = BrukerModel.Oppgave.Tilstand.valueOf(getString("tilstand")),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    utgaattTidspunkt = getObject("utgaatt_tidspunkt", OffsetDateTime::class.java),
                    utfoertTidspunkt = getObject("utfoert_tidspunkt", OffsetDateTime::class.java),
                    paaminnelseTidspunkt = getObject("paaminnelse_tidspunkt", OffsetDateTime::class.java),
                    frist = getObject("frist", LocalDate::class.java),
                    id = getUuid("id"),
                    klikketPaa = getBoolean("klikketPaa")
                )
                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }
    }

    private val searchQuerySplit = Regex("""[ \t\n\r.,:;]""")

    override suspend fun hentSaker(
        fnr: String,
        virksomhetsnummer: List<String>,
        tilganger: Tilganger,
        tekstsoek: String?,
        sakstyper: List<String>?,
        offset: Int,
        limit: Int,
        sortering: BrukerAPI.SakSortering,
        oppgaveTilstand: List<BrukerModel.Oppgave.Tilstand>?,
    ): BrukerRepository.HentSakerResultat {
        return timer.coRecord {
            val tilgangerAltinnMottaker = tilganger.tjenestetilganger.map {
                AltinnMottaker(
                    serviceCode = it.servicecode,
                    serviceEdition = it.serviceedition,
                    virksomhetsnummer = it.virksomhet
                )
            }.filter {  virksomhetsnummer.contains(it.virksomhetsnummer) }

            var tekstsoekElementer = (tekstsoek ?: "")
                .trim()
                .lowercase()
                .split(searchQuerySplit)
                .map { it.replace("\\", "\\\\") }
                .map { it.replace("_", "\\_") }
                .map { it.replace("%", "\\%") }
                .filter { it != "" }

            val truncateSearchTerms = 10
            if (tekstsoekElementer.size > truncateSearchTerms) {
                log.warn("{} search terms; truncating to {}.", tekstsoekElementer.size, truncateSearchTerms)
                tekstsoekElementer = tekstsoekElementer.take(truncateSearchTerms)
            }

            val tekstsoekSql = tekstsoekElementer
                .joinToString(separator = " and ") { """ search.text like '%' || ? || '%' """ }
                .ifNotBlank { "where $it" }

            val rows = database.nonTransactionalExecuteQuery(
                /*  quotes are necessary for fields from json, otherwise they are lower-cased */
                """
                    with 
                        mine_altinntilganger as (
                            select
                                virksomhetsnummer as virksomhet,
                                "serviceCode" as service_code,
                                "serviceEdition" as service_edition from json_to_recordset(?::json) 
                            as (virksomhetsnummer text, "serviceCode" text, "serviceEdition" text)
                        ),
                        mine_altinn_saker as (
                            select er.sak_id
                            from mottaker_altinn_enkeltrettighet er
                            join mine_altinntilganger using (virksomhet, service_code, service_edition)
                            where er.sak_id is not null
                        ),
                        mine_digisyfo_saker as (
                            select sak_id
                            from mottaker_digisyfo_for_fnr
                            where fnr_leder = ? and virksomhet = any(?) and sak_id is not null
                        ),
                        mine_sak_ider as (
                            (select * from mine_digisyfo_saker)
                            union 
                            (select * from mine_altinn_saker)
                        ),
                        mine_saker as (
                            select s.*
                            from mine_sak_ider as ms
                            join sak_search as search on ms.sak_id = search.id
                            join sak as s on s.id = ms.sak_id
                            $tekstsoekSql
                        ),
                        mine_saker_med_oppgaver as (
                            select 
                            s.*,
                            o.id as oppgave_id, 
                            o.tilstand as oppgave_tilstand, 
                            o.frist as oppgave_frist, 
                            o.paaminnelse_tidspunkt as oppgave_paaminnelse_tidspunkt
                            from mine_saker s
                            left join notifikasjon as o
                                on 
                                    o.merkelapp = s.merkelapp 
                                    and o.grupperingsid = s.grupperingsid
                                    and o.type = 'OPPGAVE'
                        ),
                        
                        -- Beregn antall saker pr. merkelap
                        mine_saker_oppgave_tilstandfiltrert as (
                            select * 
                            from mine_saker_med_oppgaver
                            where coalesce(coalesce(oppgave_tilstand, 'IngenTilstand') = any(?), true)
                        ),
                        mine_merkelapper as (
                            select 
                                merkelapp as sakstype,
                                count(distinct id) as antall
                            from mine_saker_oppgave_tilstandfiltrert
                            group by merkelapp
                        ),
                        
                        -- Beregn antall saker pr. oppgave-tilstand
                        mine_saker_sakstypefiltrert as (
                            select * 
                            from mine_saker_med_oppgaver
                            where coalesce(merkelapp = any(?), true)
                        ),
                        mine_oppgavetilstander as (
                            select 
                                oppgave_tilstand as tilstand,
                                count(distinct id) as antall
                            from mine_saker_sakstypefiltrert
                            where oppgave_tilstand is not null
                            group by tilstand
                        ),
                        
                        mine_saker_filtrert as (
                            select * 
                            from mine_saker_med_oppgaver
                            where coalesce(merkelapp = any(?), true)
                            and coalesce(coalesce(oppgave_tilstand, 'IngenTilstand') = any(?), true)
                        ),
                        mine_saker_aggregerte_oppgaver_uten_statuser as (
                           select 
                                id,
                                virksomhetsnummer,
                                tittel,
                                lenke,
                                merkelapp,
                                grupperingsid,
                                sist_endret_tidspunkt,
                                opprettet_tidspunkt,
                                count(*) filter (where oppgave_tilstand = 'NY') as nye_oppgaver,
                                min(oppgave_frist) filter (where oppgave_tilstand = 'NY') as tidligste_frist
                            from mine_saker_filtrert
                            group by id, virksomhetsnummer, tittel, lenke, merkelapp, grupperingsid, sist_endret_tidspunkt, opprettet_tidspunkt
                        ),
                        mine_saker_paginert as (
                            select 
                                sak.id,
                                sak.virksomhetsnummer,
                                sak.tittel,
                                sak.lenke,
                                sak.merkelapp,
                                sak.opprettet_tidspunkt,
                                sak.grupperingsid
                            from mine_saker_aggregerte_oppgaver_uten_statuser sak
                            order by ${
                                when (sortering) {
                                    BrukerAPI.SakSortering.OPPDATERT -> "sak.sist_endret_tidspunkt desc"
                                    BrukerAPI.SakSortering.OPPRETTET -> """
                                       sak.opprettet_tidspunkt desc 
                                    """
                                    BrukerAPI.SakSortering.FRIST -> """
                                        sak.tidligste_frist nulls last, sak.nye_oppgaver desc, sak.sist_endret_tidspunkt desc
                                    """
                                }
                            }
                            limit ? offset ?
                        )
                    select
                        (select count(distinct id) 
                            from mine_saker_filtrert
                            ) as totalt_antall_saker,
                    
                        (select coalesce(jsonb_agg( jsonb_build_object('navn', sakstype, 'antall', antall)), '[]'::jsonb)
                            from mine_merkelapper
                        ) as sakstyper,
                        
                        (select coalesce(jsonb_agg( jsonb_build_object('navn', tilstand, 'antall', antall)), '[]'::jsonb)
                            from mine_oppgavetilstander
                        ) as oppgavetilstander,
                            
                        (select 
                            coalesce(jsonb_agg(jsonb_build_object(
                                'sakId', id,
                                'virksomhetsnummer', virksomhetsnummer,
                                'tittel', tittel,
                                'lenke', lenke,
                                'merkelapp', merkelapp,
                                'opprettetTidspunkt', opprettet_tidspunkt,
                                'grupperingsid', grupperingsid
                            )), '[]'::jsonb) 
                            from mine_saker_paginert
                        ) as saker
                        
                    """,
                {
                    jsonb(tilgangerAltinnMottaker)
                    text(fnr)
                    textArray(virksomhetsnummer)
                    tekstsoekElementer.forEach { text(it) }
                    nullableEnumAsTextList(oppgaveTilstand)
                    nullableTextArray(sakstyper)
                    nullableTextArray(sakstyper)
                    nullableEnumAsTextList(oppgaveTilstand)
                    integer(limit)
                    integer(offset)
                }
            ) {
                BrukerRepository.HentSakerResultat(
                    totaltAntallSaker = getInt("totalt_antall_saker"),
                    saker = laxObjectMapper.readValue(getString("saker")),
                    sakstyper = laxObjectMapper.readValue(getString("sakstyper")),
                    oppgaveTilstanderMedAntall = laxObjectMapper.readValue(getString("oppgavetilstander"))
                )
            }
            return@coRecord rows.first()
        }
    }

    override suspend fun berikSaker(
        saker: List<BrukerModel.Sak>,
    ): Map<UUID, BrukerModel.Sakberikelse> {
        if (saker.isEmpty()) {
            return mapOf()
        }

        val tidslinjer = database.nonTransactionalExecuteQuery(
            /*  quotes are necessary for fields from json, otherwise they are lower-cased */
            """
            with saker as (
                select grupperingsid, merkelapp
                from json_to_recordset(?::json) as (grupperingsid text, merkelapp text)
            )
            select n.* 
            from notifikasjon n
            join saker s on s.grupperingsid = n.grupperingsid and s.merkelapp = n.merkelapp
            """,
            {
                jsonb(saker.map { it.gruppering })
            }
        ) {
            when (val type = getString("type")) {
                "BESKJED" -> BrukerModel.TidslinjeElement.Beskjed(
                    id = getUuid("id"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java).toInstant(),
                )

                "OPPGAVE" -> BrukerModel.TidslinjeElement.Oppgave(
                    id = getUuid("id"),
                    tilstand = BrukerModel.Oppgave.Tilstand.valueOf(getString("tilstand")),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java).toInstant(),
                    utgaattTidspunkt = getObject("utgaatt_tidspunkt", OffsetDateTime::class.java)?.toInstant(),
                    utfoertTidspunkt = getObject("utfoert_tidspunkt", OffsetDateTime::class.java)?.toInstant(),
                    paaminnelseTidspunkt = getObject("paaminnelse_tidspunkt", OffsetDateTime::class.java)?.toInstant(),
                    frist = getObject("frist", LocalDate::class.java),
                )

                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }
            .groupBy { it.grupperingsid }
            .mapValues { it.value.sortedByDescending { it.opprettetTidspunkt } }

        val sisteStatuser = database.nonTransactionalExecuteQuery("""
            with sak_status_med_rank as (
              select
                    sak_id,
                    status,
                    overstyrt_statustekst,
                    tidspunkt,
                    rank() over (partition by sak_id order by tidspunkt desc) dest_rank
                from sak_status
                where sak_id = any(?)
            )
            select 
                sak_id, 
                json_build_object(
                    'status', status,
                    'overstyrtStatustekst', overstyrt_statustekst,
                    'tidspunkt', tidspunkt
                ) as siste_status
            from sak_status_med_rank where dest_rank = 1
        """,
            {
                uuidArray(saker.map { it.sakId })
            }
        ) {
            getUuid("sak_id") to laxObjectMapper.readValue<BrukerModel.SakStatus>(getString("siste_status"))
        }
            .toMap()

        return saker.associateBy({ it.sakId }) {
            BrukerModel.Sakberikelse(
                sisteStatus = sisteStatuser[it.sakId],
                tidslinje = when (it.grupperingsid) {
                    null -> listOf()
                    else -> tidslinjer[it.grupperingsid].orEmpty()
                }
            )
        }
    }

    override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String? =
        database.nonTransactionalExecuteQuery(
            """
                SELECT virksomhetsnummer FROM notifikasjon WHERE id = ? LIMIT 1
            """, {
                uuid(notifikasjonsid)
            }) {
            getString("virksomhetsnummer")!!
        }.getOrNull(0)

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        if (erHardDeleted(hendelse.aggregateId)) {
            log.info("skipping harddeleted event {}", hendelse)
            return
        }

        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored: Unit = when (hendelse) {
            is SakOpprettet -> oppdaterModellEtterSakOpprettet(hendelse, metadata)
            is NyStatusSak -> oppdaterModellEtterNyStatusSak(hendelse)
            is BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is BrukerKlikket -> oppdaterModellEtterBrukerKlikket(hendelse)
            is OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is OppgaveUtført -> oppdaterModellEtterOppgaveUtført(hendelse, metadata)
            is OppgaveUtgått -> oppdaterModellEtterOppgaveUtgått(hendelse)
            is SoftDelete -> oppdaterModellEtterDelete(hendelse.aggregateId)
            is HardDelete -> oppdaterModellEtterDelete(hendelse.aggregateId) { tx ->
                registrerHardDelete(tx, hendelse)
            }
            is EksterntVarselFeilet -> Unit
            is EksterntVarselVellykket -> Unit
            is PåminnelseOpprettet -> oppdaterModellEtterPåminnelseOpprettet(hendelse)
            is FristUtsatt -> oppdaterModellEtterFristUtsatt(hendelse)
        }
    }

    override suspend fun hentSakerForNotifikasjoner(
        grupperinger: List<BrukerModel.Gruppering>,
    ): Map<String, String> = timer.coRecord {
        val rows = database.nonTransactionalExecuteQuery(
            /*  quotes are necessary for fields from json, otherwise they are lower-cased */
            """
                with gruppering as (
                    select grupperingsid, merkelapp
                    from json_to_recordset(?::json) as (grupperingsid text, merkelapp text)
                )
                select s.*
                    from sak s
                    join gruppering g on g.grupperingsid = s.grupperingsid and g.merkelapp = s.merkelapp
                """,
            {
                jsonb(grupperinger)
            }
        ) {
            getString("grupperingsid") to getString("tittel")
        }
        return@coRecord rows.toMap()
    }

    private suspend fun oppdaterModellEtterDelete(aggregateId: UUID, callback: (tx: Transaction) -> Unit = {}) {
        database.transaction({
            throw RuntimeException("Delete", it)
        }) {
            executeUpdate(""" DELETE FROM notifikasjon WHERE id = ?;""") {
                uuid(aggregateId)
            }

            executeUpdate("""DELETE FROM brukerklikk WHERE notifikasjonsid = ?;""") {
                uuid(aggregateId)
            }

            executeUpdate(""" DELETE FROM sak WHERE id = ?;""") {
                uuid(aggregateId)
            }

            callback(this)
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtført(utførtHendelse: OppgaveUtført, metdata: HendelseMetadata) {
        database.nonTransactionalExecuteUpdate(
            """
            UPDATE notifikasjon
            SET 
            tilstand = '${ProdusentModel.Oppgave.Tilstand.UTFOERT}',
            utfoert_tidspunkt = ?,
            lenke = coalesce(?, lenke)
            WHERE id = ?
        """
        ) {
            timestamp_with_timezone(utførtHendelse.utfoertTidspunkt?: metdata.timestamp.atOffset(ZoneOffset.UTC))
            nullableText(utførtHendelse.nyLenke)
            uuid(utførtHendelse.notifikasjonId)
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtgått(utgåttHendelse: OppgaveUtgått) {
        database.nonTransactionalExecuteUpdate(
            """
            UPDATE notifikasjon
            SET tilstand = '${ProdusentModel.Oppgave.Tilstand.UTGAATT}',
                utgaatt_tidspunkt = ?,
                lenke = coalesce(?, lenke)
            WHERE id = ?
        """
        ) {
            timestamp_with_timezone(utgåttHendelse.utgaattTidspunkt)
            nullableText(utgåttHendelse.nyLenke)
            uuid(utgåttHendelse.notifikasjonId)
        }
    }


    private suspend fun oppdaterModellEtterBrukerKlikket(brukerKlikket: BrukerKlikket) {
        database.nonTransactionalExecuteUpdate(
            """
            insert into brukerklikk(fnr, notifikasjonsid) values (?, ?)
            on conflict do nothing
        """
        ) {
            text(brukerKlikket.fnr)
            uuid(brukerKlikket.notifikasjonId)
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
                storeMottaker(
                    notifikasjonId = beskjedOpprettet.notifikasjonId,
                    sakId = null,
                    mottaker
                )
            }
        }
    }

    private suspend fun oppdaterModellEtterSakOpprettet(
        sakOpprettet: SakOpprettet,
        hendelseMetadata: HendelseMetadata,
    ) {
        database.transaction {
            executeUpdate(
                """
                insert into sak(
                    id, virksomhetsnummer, tittel, lenke, merkelapp, grupperingsid, sist_endret_tidspunkt, opprettet_tidspunkt
                )
                values (?, ?, ? ,?, ?, ?, ?, ?)
                on conflict do nothing;
            """
            ) {
                uuid(sakOpprettet.sakId)
                text(sakOpprettet.virksomhetsnummer)
                text(sakOpprettet.tittel)
                text(sakOpprettet.lenke)
                text(sakOpprettet.merkelapp)
                text(sakOpprettet.grupperingsid)
                instantAsText(sakOpprettet.opprettetTidspunkt(hendelseMetadata.timestamp))
                instantAsText(sakOpprettet.opprettetTidspunkt(hendelseMetadata.timestamp))
            }


            /** Slettes etter migrering er kjørt og feltene har fått "not null"-constraint.
             *  NB. `greatest('foo', null) = 'foo'`, og `least('foo', null) = 'foo'`.
             **/
            executeUpdate("""
                update sak
                set 
                    sist_endret_tidspunkt = greatest(sist_endret_tidspunkt, ?),
                    opprettet_tidspunkt = least(opprettet_tidspunkt, ?)
                where 
                    id = ?
            """) {
                instantAsText(sakOpprettet.opprettetTidspunkt(hendelseMetadata.timestamp))
                instantAsText(sakOpprettet.opprettetTidspunkt(hendelseMetadata.timestamp))
                uuid(sakOpprettet.sakId)
            }

            executeUpdate(
                """
                insert into sak_search(id, text)
                values (?, lower(?)) 
                on conflict (id) do update
                set text = lower(?)
            """
            ) {
                uuid(sakOpprettet.sakId)
                text("${sakOpprettet.tittel} ${sakOpprettet.merkelapp}")
                text("${sakOpprettet.tittel} ${sakOpprettet.merkelapp}")
            }

            for (mottaker in sakOpprettet.mottakere) {
                storeMottaker(
                    notifikasjonId = null,
                    sakId = sakOpprettet.sakId,
                    mottaker
                )
            }
        }
    }

    private suspend fun oppdaterModellEtterNyStatusSak(nyStatusSak: NyStatusSak) {
        database.transaction {
            executeUpdate(
                """
                insert into sak_status(
                    id, sak_id, status, overstyrt_statustekst, tidspunkt 
                )
                values (?, ?, ?, ?, ?)
                on conflict do nothing;
            """
            ) {
                uuid(nyStatusSak.hendelseId)
                uuid(nyStatusSak.sakId)
                text(nyStatusSak.status.name)
                nullableText(nyStatusSak.overstyrStatustekstMed)
                timestamp_with_timezone(nyStatusSak.oppgittTidspunkt ?: nyStatusSak.mottattTidspunkt)
            }

            executeUpdate("""
                update sak
                set sist_endret_tidspunkt = greatest(sist_endret_tidspunkt, ?)
                where id = ?
            """) {
                instantAsText((nyStatusSak.oppgittTidspunkt ?: nyStatusSak.mottattTidspunkt).toInstant())
                uuid(nyStatusSak.sakId)
            }

            if (nyStatusSak.nyLenkeTilSak != null) {
                executeUpdate(
                    """
                    UPDATE sak
                    SET lenke = ?
                    WHERE id = ?
                """
                ) {
                    text(nyStatusSak.nyLenkeTilSak)
                    uuid(nyStatusSak.sakId)
                }
            }
        }
    }

    private suspend fun oppdaterModellEtterPåminnelseOpprettet(påminnelseOpprettet: PåminnelseOpprettet) {
        database.transaction {
            executeUpdate(
                """
                    update notifikasjon
                    set paaminnelse_tidspunkt = ?
                    where id = ?
                """
            ) {
                timestamp_with_timezone(påminnelseOpprettet.tidspunkt.påminnelseTidspunkt.atOffset(ZoneOffset.UTC))
                uuid(påminnelseOpprettet.notifikasjonId)
            }
            executeUpdate("delete from brukerklikk where notifikasjonsid = ?;") {
                uuid(påminnelseOpprettet.notifikasjonId)
            }
        }
    }

    private fun Transaction.storeMottaker(notifikasjonId: UUID?, sakId: UUID?, mottaker: Mottaker) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored = when (mottaker) {
            is NærmesteLederMottaker -> storeNærmesteLederMottaker(
                notifikasjonId = notifikasjonId,
                sakId = sakId,
                mottaker = mottaker
            )
            is AltinnMottaker -> storeAltinnMottaker(
                notifikasjonId = notifikasjonId,
                sakId = sakId,
                mottaker = mottaker
            )
            is HendelseModel._AltinnRolleMottaker -> basedOnEnv(
                prod = { throw java.lang.RuntimeException("AltinnRolleMottaker støttes ikke i prod") },
                other = {  },
            )
            is HendelseModel._AltinnReporteeMottaker -> basedOnEnv(
                prod = { throw java.lang.RuntimeException("AltinnReporteeMottaker støttes ikke i prod") },
                other = {  },
            )
        }
    }

    private fun Transaction.storeNærmesteLederMottaker(
        notifikasjonId: UUID?,
        sakId: UUID?,
        mottaker: NærmesteLederMottaker
    ) {
        executeUpdate(
            """
            insert into mottaker_digisyfo(notifikasjon_id, sak_id, virksomhet, fnr_leder, fnr_sykmeldt)
            values (?, ?, ?, ?, ?)
            on conflict do nothing
        """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            text(mottaker.virksomhetsnummer)
            text(mottaker.naermesteLederFnr)
            text(mottaker.ansattFnr)
        }
    }

    private fun Transaction.storeAltinnMottaker(
        notifikasjonId: UUID?,
        sakId: UUID?,
        mottaker: AltinnMottaker
    ) {
        executeUpdate(
            """
            insert into mottaker_altinn_enkeltrettighet
                (notifikasjon_id, sak_id, virksomhet, service_code, service_edition)
            values (?, ?, ?, ?, ?)
            on conflict do nothing
        """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            text(mottaker.virksomhetsnummer)
            text(mottaker.serviceCode)
            text(mottaker.serviceEdition)
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
                storeMottaker(
                    notifikasjonId = oppgaveOpprettet.notifikasjonId,
                    sakId = null,
                    mottaker
                )
            }
        }
    }

    override suspend fun oppdaterModellEtterNærmesteLederLeesah(nærmesteLederLeesah: NarmesteLederLeesah) {
        if (nærmesteLederLeesah.aktivTom != null) {
            database.nonTransactionalExecuteUpdate(
                """
                delete from naermeste_leder_kobling where id = ?
            """
            ) {
                uuid(nærmesteLederLeesah.narmesteLederId)
            }
        } else {
            database.nonTransactionalExecuteUpdate(
                """
                INSERT INTO naermeste_leder_kobling(id, fnr, naermeste_leder_fnr, orgnummer)
                VALUES(?, ?, ?, ?) 
                ON CONFLICT (id) 
                DO 
                UPDATE SET 
                    orgnummer = EXCLUDED.orgnummer, 
                    fnr = EXCLUDED.fnr, 
                    naermeste_leder_fnr = EXCLUDED.naermeste_leder_fnr;
            """
            ) {
                uuid(nærmesteLederLeesah.narmesteLederId)
                text(nærmesteLederLeesah.fnr)
                text(nærmesteLederLeesah.narmesteLederFnr)
                text(nærmesteLederLeesah.orgnummer)
            }
        }
    }

    override suspend fun hentSakstyper(fnr: String, tilganger: Tilganger): List<String> {
        return timer.coRecord {
            val tilgangerAltinnMottaker = tilganger.tjenestetilganger.map {
                AltinnMottaker(
                    serviceCode = it.servicecode,
                    serviceEdition = it.serviceedition,
                    virksomhetsnummer = it.virksomhet
                )
            }

            val rows = database.nonTransactionalExecuteQuery(
                /*  quotes are necessary for fields from json, otherwise they are lower-cased */
                """
                    with 
                        mine_altinntilganger as (
                            select * from json_to_recordset(?::json) 
                            as (virksomhetsnummer text, "serviceCode" text, "serviceEdition" text)
                        ),
                        mine_altinn_saker as (
                            select er.sak_id
                            from mottaker_altinn_enkeltrettighet er
                            join mine_altinntilganger at on 
                                er.virksomhet = at.virksomhetsnummer and
                                er.service_code = at."serviceCode" and
                                er.service_edition = at."serviceEdition"
                            where
                                er.sak_id is not null
                        ),
                        mine_digisyfo_saker as (
                            select sak_id
                            from mottaker_digisyfo_for_fnr
                            where fnr_leder = ? and sak_id is not null
                        ),
                        mine_sak_ider as (
                            (select * from mine_digisyfo_saker)
                            union 
                            (select * from mine_altinn_saker)
                        )
                        select distinct s.merkelapp as merkelapp 
                        from mine_sak_ider as ms
                        join sak as s on s.id = ms.sak_id
                    """,
                {
                    jsonb(tilgangerAltinnMottaker)
                    text(fnr)
                }
            ) {
                getString("merkelapp")
            }
            return@coRecord rows
        }
    }

    private suspend fun oppdaterModellEtterFristUtsatt(hendelse: FristUtsatt) {
        database.nonTransactionalExecuteUpdate("""
            update notifikasjon
            set tilstand = '${BrukerModel.Oppgave.Tilstand.NY}',
                frist = ?,
                utgaatt_tidspunkt = null,
                paaminnelse_tidspunkt = null
            where
                id = ? and tilstand <> '${BrukerModel.Oppgave.Tilstand.UTFOERT}'
        """) {
            date(hendelse.frist)
            uuid(hendelse.notifikasjonId)
        }
    }
}
