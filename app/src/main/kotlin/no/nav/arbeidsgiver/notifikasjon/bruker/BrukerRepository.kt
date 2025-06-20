package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.OppgaveFilterInfo.OppgaveFilterType
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Kalenderavtale.Tilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
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
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NesteStegSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtgått
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.TilleggsinformasjonSak
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnTilganger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.nærmeste_leder.NarmesteLederLeesah
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import java.time.*
import java.util.*

interface BrukerRepository {
    suspend fun hentNotifikasjoner(
        fnr: String,
        altinnTilganger: AltinnTilganger,
    ): List<BrukerModel.Notifikasjon>

    class HentSakerResultat(
        val totaltAntallSaker: Int,
        val saker: List<BrukerModel.Sak>,
        val sakstyper: List<Sakstype>,
        val oppgaveTilstanderMedAntall: List<Oppgavetilstand>,
        val oppgaveFilterMedAntall: List<OppgaveFilterInfo>,
    )

    class OppgaveFilterInfo(
        val filterType: String,
        val antall: Int
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
        altinnTilganger: AltinnTilganger,
        tekstsoek: String?,
        sakstyper: List<String>?,
        sortering: BrukerAPI.SakSortering,
        offset: Int,
        limit: Int,
        oppgaveTilstand: List<BrukerModel.Oppgave.Tilstand>?,
        oppgaveFilter: List<OppgaveFilterType>?
    ): HentSakerResultat

    suspend fun hentSakById(
        fnr: String,
        altinnTilganger: AltinnTilganger,
        id: UUID,
    ): BrukerModel.Sak?

    suspend fun hentSakByGrupperingsid(
        fnr: String,
        altinnTilganger: AltinnTilganger,
        grupperingsid: String,
        merkelapp: String,
    ): BrukerModel.Sak?

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
    suspend fun hentSakstyper(fnr: String, altinnTilganger: AltinnTilganger): List<String>
    suspend fun hentSakerForNotifikasjoner(
        grupperinger: List<BrukerModel.Gruppering>
    ): Map<String, BrukerModel.SakMetadata>

    suspend fun hentKommendeKalenderavaler(
        fnr: String,
        virksomhetsnumre: List<String>,
        altinnTilganger: AltinnTilganger
    ): List<BrukerModel.Kalenderavtale>

    suspend fun settNotifikasjonerSistLest(tidspunkt: OffsetDateTime, fnr: String)
    suspend fun hentNotifikasjonerSistLest(fnr: String): OffsetDateTime?
}

class BrukerRepositoryImpl(
    private val database: Database
) : BrukerRepository, HardDeletedRepository(database) {
    private val log = logger()
    private val timer = Metrics.meterRegistry.timer("query_model_repository_hent_notifikasjoner")

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse, metadata: HendelseMetadata) {
        if (hendelse is HendelseModel.AggregatOpprettet) {
            registrerKoblingForCascadeDelete(hendelse)
        }
        if (erHardDeleted(hendelse.aggregateId)) {
            log.info("skipping harddeleted event {}", hendelse)
            return
        }

        when (hendelse) {
            is SakOpprettet -> oppdaterModellEtterSakOpprettet(hendelse, metadata)
            is NyStatusSak -> oppdaterModellEtterNyStatusSak(hendelse)
            is BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is BrukerKlikket -> oppdaterModellEtterBrukerKlikket(hendelse)
            is OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is OppgaveUtført -> oppdaterModellEtterOppgaveUtført(hendelse, metadata)
            is OppgaveUtgått -> oppdaterModellEtterOppgaveUtgått(hendelse)
            is SoftDelete -> oppdaterModellEtterDelete(hendelse.aggregateId, hendelse.grupperingsid, hendelse.merkelapp)
            is HardDelete -> oppdaterModellEtterDelete(hendelse.aggregateId, hendelse.grupperingsid, hendelse.merkelapp)
            is EksterntVarselFeilet -> Unit
            is EksterntVarselVellykket -> Unit
            is EksterntVarselKansellert -> Unit
            is PåminnelseOpprettet -> oppdaterModellEtterPåminnelseOpprettet(hendelse)
            is FristUtsatt -> oppdaterModellEtterFristUtsatt(hendelse)
            is HendelseModel.KalenderavtaleOpprettet -> oppdaterModellEtterKalenderavtaleOpprettet(hendelse)
            is HendelseModel.KalenderavtaleOppdatert -> oppdaterModellEtterKalenderavtaleOppdatert(hendelse)
            is NesteStegSak -> oppdaterModellEtterNesteStegSak(hendelse)
            is TilleggsinformasjonSak -> oppdaterModellEtterTilleggsinformasjonSak(hendelse)
            is HendelseModel.OppgavePåminnelseEndret -> Unit
        }
    }

    override suspend fun hentNotifikasjoner(
        fnr: String,
        altinnTilganger: AltinnTilganger,
    ): List<BrukerModel.Notifikasjon> = timer.coRecord {

        database.nonTransactionalExecuteQuery(
            """
            with 
                mine_altinntilganger as (
                    select * from json_to_recordset(?::json) 
                    as ("orgNr" text, tilgang text)
                ),
                mine_altinn_notifikasjoner as (
                    select er.notifikasjon_id
                    from mottaker_altinn_tilgang er
                    join mine_altinntilganger at on 
                        er.virksomhet = at."orgNr" and
                        er.altinn_tilgang = at.tilgang
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
                jsonb(altinnTilganger.tilganger)
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

                "KALENDERAVTALE" -> BrukerModel.Kalenderavtale(
                    merkelapp = getString("merkelapp"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    paaminnelseTidspunkt = getObject("paaminnelse_tidspunkt", OffsetDateTime::class.java),
                    id = getUuid("id"),
                    klikketPaa = getBoolean("klikketPaa"),
                    startTidspunkt = getString("start_tidspunkt").let { LocalDateTime.parse(it) },
                    sluttTidspunkt = getString("slutt_tidspunkt")?.let { LocalDateTime.parse(it) },
                    lokasjon = getString("lokasjon")?.let { laxObjectMapper.readValue(it) },
                    erDigitalt = getBoolean("digitalt"),
                    tilstand = BrukerModel.Kalenderavtale.Tilstand.valueOf(getString("tilstand")),
                )

                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }
    }

    private val searchQuerySplit = Regex("""[ \t\n\r.,:;]""")

    private fun OppgaveFilterType.toOppgaveTilstand(): BrukerModel.Oppgave.Tilstand? {
        return when (this) {
            OppgaveFilterType.TILSTAND_NY -> BrukerModel.Oppgave.Tilstand.NY
            OppgaveFilterType.TILSTAND_UTFOERT -> BrukerModel.Oppgave.Tilstand.UTFOERT
            OppgaveFilterType.TILSTAND_UTGAATT -> BrukerModel.Oppgave.Tilstand.UTGAATT
            OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST -> null
        }
    }

    private fun BrukerModel.Oppgave.Tilstand.toOppgaveFilterType(): OppgaveFilterType {
        return when (this) {
            BrukerModel.Oppgave.Tilstand.NY -> OppgaveFilterType.TILSTAND_NY
            BrukerModel.Oppgave.Tilstand.UTFOERT -> OppgaveFilterType.TILSTAND_UTFOERT
            BrukerModel.Oppgave.Tilstand.UTGAATT -> OppgaveFilterType.TILSTAND_UTGAATT
        }
    }

    override suspend fun hentSaker(
        fnr: String,
        virksomhetsnummer: List<String>,
        altinnTilganger: AltinnTilganger,
        tekstsoek: String?,
        sakstyper: List<String>?,
        sortering: BrukerAPI.SakSortering,
        offset: Int,
        limit: Int,
        oppgaveTilstand: List<BrukerModel.Oppgave.Tilstand>?, //TODO: Fjern denne etter deploy av frontend
        oppgaveFilter: List<OppgaveFilterType>?
    ): BrukerRepository.HentSakerResultat {
        return timer.coRecord {

            //TODO: fjerne dette etter deploy av frontend
            val oppgaveFilter = oppgaveFilter ?: oppgaveTilstand?.map { it.toOppgaveFilterType() }
            val oppgaveTilstand = oppgaveFilter?.mapNotNull { it.toOppgaveTilstand() }

            val harPåminnelse =
                oppgaveFilter?.any { it == OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST } == true

            val tilgangerAltinnMottaker = altinnTilganger.tilganger
                .filter { virksomhetsnummer.contains(it.orgNr) }

            var tekstsoekElementer = (tekstsoek ?: "")
                .trim()
                .lowercase()
                .split(searchQuerySplit)
                .map { it.replace("\\", "\\\\") }
                .map { it.replace("_", "\\_") }
                .map { it.replace("%", "\\%") }
                .flatMap {
                    if (it.matches(Regex("""\d{4,}"""))) {
                        it.chunked(2)
                    } else listOf(it)
                }
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
                """
                    with 
                        mine_altinntilganger as (
                            select * from json_to_recordset(?::json)  
                            as ("orgNr" text, tilgang text)
                        ),
                        mine_altinn_saker as (
                            select er.sak_id
                            from mottaker_altinn_tilgang er
                            join mine_altinntilganger at on 
                                er.virksomhet = at."orgNr" and
                                er.altinn_tilgang = at.tilgang
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
                            (case
                                when o.type = 'KALENDERAVTALE' and o.tilstand = '$VENTER_SVAR_FRA_ARBEIDSGIVER'
                                    then 'NY'
                                when o.type = 'OPPGAVE'
                                    then o.tilstand
                            end)
                                as oppgave_tilstand, 
                            o.frist as oppgave_frist, 
                            o.paaminnelse_tidspunkt as oppgave_paaminnelse_tidspunkt
                            from mine_saker s
                            left join notifikasjon as o
                                on 
                                    o.merkelapp = s.merkelapp 
                                    and o.grupperingsid = s.grupperingsid
                                    and (
                                        o.type = 'OPPGAVE' or 
                                        (o.type = 'KALENDERAVTALE' and o.tilstand = '$VENTER_SVAR_FRA_ARBEIDSGIVER')
                                    )
                        ),
                        
                        -- Beregn antall saker pr. merkelap med filter på oppgave_tilstand og påminnelse utløst
                        mine_saker_oppgave_filtrert as (
                            select * 
                            from mine_saker_med_oppgaver
                            where coalesce(coalesce(oppgave_tilstand, 'IngenTilstand') = any(?), true)
                            ${
                    if (harPåminnelse)
                    //language=SQL
                        "and oppgave_paaminnelse_tidspunkt is not null"
                    else ""
                }
                        ),
                        mine_merkelapper as (
                            select 
                                merkelapp as sakstype,
                                count(distinct id) as antall
                            from mine_saker_oppgave_filtrert
                            group by merkelapp
                        ),
                        
                        -- Beregn antall saker pr. oppgave-tilstand med filter på merkelapp
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
                                                                        
                        mine_oppgavefilter as (
                            select 
                                oppgave_tilstand as filtertype,
                                count(distinct id) as antall
                            from mine_saker_sakstypefiltrert
                            where oppgave_tilstand is not null
                            group by filterType
                            union
                            select 
                                '${OppgaveFilterType.TILSTAND_NY_MED_PAAMINNELSE_UTLOEST.name}' as filtertype,
                                count(distinct id) as antall
                            from mine_saker_sakstypefiltrert
                            where oppgave_tilstand = 'NY' and oppgave_paaminnelse_tidspunkt is not null
                            group by filterType
                        ),                                          
                        
                        mine_saker_filtrert as (
                            select * 
                            from mine_saker_med_oppgaver
                            where coalesce(merkelapp = any(?), true)
                            and coalesce(coalesce(oppgave_tilstand, 'IngenTilstand') = any(?), true)
                            ${
                    if (harPåminnelse)
                    //language=SQL
                        "and oppgave_paaminnelse_tidspunkt is not null"
                    else ""
                }
                        ),
                        mine_saker_aggregerte_oppgaver_uten_statuser as (
                           select 
                                id,
                                virksomhetsnummer,
                                tittel,
                                lenke,
                                merkelapp,
                                grupperingsid,
                                neste_steg,
                                tilleggsinformasjon,
                                sist_endret_tidspunkt,
                                opprettet_tidspunkt,
                                count(*) filter (where oppgave_tilstand = 'NY') as nye_oppgaver,
                                min(oppgave_frist) filter (where oppgave_tilstand = 'NY') as tidligste_frist
                            from mine_saker_filtrert
                            group by id, virksomhetsnummer, tittel, lenke, merkelapp, grupperingsid, neste_steg, tilleggsinformasjon, sist_endret_tidspunkt, opprettet_tidspunkt
                        ),
                        mine_saker_paginert as (
                            select 
                                sak.id,
                                sak.virksomhetsnummer,
                                sak.tittel,
                                sak.lenke,
                                sak.merkelapp,
                                sak.neste_steg,
                                sak.tilleggsinformasjon,
                                sak.opprettet_tidspunkt,
                                sak.grupperingsid
                            from mine_saker_aggregerte_oppgaver_uten_statuser sak
                            order by sak.sist_endret_tidspunkt ${
                    when (sortering) {
                        BrukerAPI.SakSortering.NYESTE -> "desc"
                        BrukerAPI.SakSortering.ELDSTE -> "asc"
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
                        
                        (select coalesce(jsonb_agg( jsonb_build_object('filterType', filterType, 'antall', antall)), '[]'::jsonb)
                            from mine_oppgavefilter
                        ) as oppgavefilter,
                            
                        (select 
                            coalesce(jsonb_agg(jsonb_build_object(
                                'sakId', id,
                                'virksomhetsnummer', virksomhetsnummer,
                                'tittel', tittel,
                                'lenke', lenke,
                                'nesteSteg', neste_steg,
                                'tilleggsinformasjon', tilleggsinformasjon,
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
                    oppgaveTilstanderMedAntall = laxObjectMapper.readValue(getString("oppgavetilstander")),
                    oppgaveFilterMedAntall = laxObjectMapper.readValue(getString("oppgavefilter"))
                )
            }
            return@coRecord rows.first()
        }
    }

    override suspend fun hentSakById(
        fnr: String,
        altinnTilganger: AltinnTilganger,
        id: UUID
    ): BrukerModel.Sak? {
        return timer.coRecord {
            val rows = database.nonTransactionalExecuteQuery(
                """
                    with
                        mine_altinntilganger as (
                            select * from json_to_recordset(?::json) 
                            as ("orgNr" text, tilgang text)
                        ),
                        mine_altinn_saker as (
                            select er.sak_id
                            from mottaker_altinn_tilgang er
                            join mine_altinntilganger at on 
                                er.virksomhet = at."orgNr" and
                                er.altinn_tilgang = at.tilgang
                            where er.sak_id is not null
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
                        ),
                        min_sak as (
                            select s.*
                            from mine_sak_ider as ms
                            join sak_search as search on ms.sak_id = search.id
                            join sak as s on s.id = ms.sak_id
                            where s.id = ?
                        )
                        
                        select coalesce(jsonb_build_object(
                                'sakId', id,
                                'virksomhetsnummer', virksomhetsnummer,
                                'tittel', tittel,
                                'lenke', lenke,
                                'nesteSteg', neste_steg,
                                'tilleggsinformasjon', tilleggsinformasjon,
                                'merkelapp', merkelapp,
                                'opprettetTidspunkt', opprettet_tidspunkt,
                                'grupperingsid', grupperingsid
                            ), '[]'::jsonb) as sak from min_sak
                    """,
                {
                    jsonb(altinnTilganger.tilganger)
                    text(fnr)
                    uuid(id)
                }
            ) {
                laxObjectMapper.readValue<BrukerModel.Sak?>(getString("sak"))
            }
            return@coRecord rows.firstOrNull()
        }
    }

    override suspend fun hentSakByGrupperingsid(
        fnr: String,
        altinnTilganger: AltinnTilganger,
        grupperingsid: String,
        merkelapp: String
    ): BrukerModel.Sak? {
        return timer.coRecord {

            val rows = database.nonTransactionalExecuteQuery(
                """
                    with 
                        mine_altinntilganger as (
                            select * from json_to_recordset(?::json) 
                            as ("orgNr" text, tilgang text)
                        ),
                        mine_altinn_saker as (
                            select er.sak_id
                            from mottaker_altinn_tilgang er
                            join mine_altinntilganger at on 
                                er.virksomhet = at."orgNr" and
                                er.altinn_tilgang = at.tilgang
                            where er.sak_id is not null
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
                        ),
                        min_sak as (
                            select s.*
                            from mine_sak_ider as ms
                            join sak_search as search on ms.sak_id = search.id
                            join sak as s on s.id = ms.sak_id
                            where s.grupperingsid = ? and s.merkelapp = ?
                        )
                        
                        select coalesce(jsonb_build_object(
                                'sakId', id,
                                'virksomhetsnummer', virksomhetsnummer,
                                'tittel', tittel,
                                'lenke', lenke,
                                'nesteSteg', neste_steg,
                                'tilleggsinformasjon', tilleggsinformasjon,
                                'merkelapp', merkelapp,
                                'opprettetTidspunkt', opprettet_tidspunkt,
                                'grupperingsid', grupperingsid
                            ), '[]'::jsonb) as sak from min_sak
                    """,
                {
                    jsonb(altinnTilganger.tilganger)
                    text(fnr)
                    text(grupperingsid)
                    text(merkelapp)
                }
            ) {
                laxObjectMapper.readValue<BrukerModel.Sak?>(getString("sak"))
            }
            return@coRecord rows.firstOrNull()
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
                    lenke = getString("lenke")
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
                    lenke = getString("lenke")
                )

                "KALENDERAVTALE" -> BrukerModel.TidslinjeElement.Kalenderavtale(
                    id = getUuid("id"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java).toInstant(),
                    startTidspunkt = getString("start_tidspunkt").let { LocalDateTime.parse(it) },
                    sluttTidspunkt = getString("slutt_tidspunkt")?.let { LocalDateTime.parse(it) },
                    avtaletilstand = BrukerModel.Kalenderavtale.Tilstand.valueOf(getString("tilstand")),
                    lokasjon = getString("lokasjon")?.let { laxObjectMapper.readValue(it) },
                    digitalt = getBoolean("digitalt"),
                    lenke = getString("lenke")

                )

                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }
            .groupBy { it.grupperingsid }
            .mapValues {
                it.value.sortedByDescending { el ->
                    when (el) {
                        is BrukerModel.TidslinjeElement.Oppgave,
                        is BrukerModel.TidslinjeElement.Beskjed -> el.opprettetTidspunkt

                        is BrukerModel.TidslinjeElement.Kalenderavtale -> el.startTidspunkt.toInstant(ZoneOffset.UTC)
                    }

                }
            }

        val sisteStatuser = database.nonTransactionalExecuteQuery(
            """
            with sak_status_med_rank as (
              select
                    sak_id,
                    status,
                    overstyrt_statustekst,
                    coalesce(oppgitt_tidspunkt, mottatt_tidspunkt) as tidspunkt,
                    rank() over (partition by sak_id order by mottatt_tidspunkt desc) dest_rank
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


    override suspend fun hentSakerForNotifikasjoner(
        grupperinger: List<BrukerModel.Gruppering>,
    ): Map<String, BrukerModel.SakMetadata> = timer.coRecord {
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
            getString("grupperingsid") to BrukerModel.SakMetadata(
                tittel = getString("tittel"),
                tilleggsinformasjon = getString("tilleggsinformasjon")
            )
        }
        return@coRecord rows.toMap()
    }

    override suspend fun hentKommendeKalenderavaler(
        fnr: String,
        virksomhetsnumre: List<String>,
        altinnTilganger: AltinnTilganger
    ): List<BrukerModel.Kalenderavtale> {
        val tilgangerAltinnMottaker = altinnTilganger.tilganger.filter { virksomhetsnumre.contains(it.orgNr) }

        return database.nonTransactionalExecuteQuery(
            """
            with 
                mine_altinntilganger as (
                    select * from json_to_recordset(?::json) 
                    as ("orgNr" text, tilgang text)
                ),
                mine_altinn_notifikasjoner as (
                    select er.notifikasjon_id
                    from mottaker_altinn_tilgang er
                    join mine_altinntilganger at on 
                        er.virksomhet = at."orgNr" and
                        er.altinn_tilgang = at.tilgang
                    where
                        er.notifikasjon_id is not null
                ),
                mine_digisyfo_notifikasjoner as (
                    select notifikasjon_id 
                    from mottaker_digisyfo_for_fnr
                    where fnr_leder = ? and virksomhet = any(?) and notifikasjon_id is not null
                ),
                mine_notifikasjoner as (
                    (select * from mine_digisyfo_notifikasjoner)
                    union 
                    (select * from mine_altinn_notifikasjoner)
                )
            select 
                n.*
            from mine_notifikasjoner as mn
            join notifikasjon as n on n.id = mn.notifikasjon_id
            where 
                n.type = 'KALENDERAVTALE' and
                n.tilstand != 'AVLYST' and
                n.tilstand != 'AVHOLDT' and
                n.start_tidspunkt::timestamp > now() and
                n.virksomhetsnummer = any(?)
            order by 
                start_tidspunkt
            limit 50
            """,
            {
                jsonb(tilgangerAltinnMottaker)
                text(fnr)
                textArray(virksomhetsnumre)
                textArray(virksomhetsnumre)
            }
        ) {
            when (val type = getString("type")) {
                "KALENDERAVTALE" -> BrukerModel.Kalenderavtale(
                    merkelapp = getString("merkelapp"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    virksomhetsnummer = getString("virksomhetsnummer"),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    paaminnelseTidspunkt = getObject("paaminnelse_tidspunkt", OffsetDateTime::class.java),
                    id = getUuid("id"),
                    klikketPaa = false, // trenger ikke klikket på i denne sammenheng
                    startTidspunkt = getString("start_tidspunkt").let { LocalDateTime.parse(it) },
                    sluttTidspunkt = getString("slutt_tidspunkt")?.let { LocalDateTime.parse(it) },
                    lokasjon = getString("lokasjon")?.let { laxObjectMapper.readValue(it) },
                    erDigitalt = getBoolean("digitalt"),
                    tilstand = BrukerModel.Kalenderavtale.Tilstand.valueOf(getString("tilstand")),
                )

                else ->
                    throw Exception("Uforventet notifikasjonstype '$type' for søk på kalenderavtale")
            }
        }
    }

    override suspend fun settNotifikasjonerSistLest(tidspunkt: OffsetDateTime, fnr: String) {
        database.nonTransactionalExecuteUpdate(
            """
            insert into notifikasjoner_sist_lest (fnr, tidspunkt) values (?, ?)
            on conflict (fnr) do update set tidspunkt = ?
        """.trimIndent(),
            {
                text(fnr)
                timestamp_with_timezone(tidspunkt)
                timestamp_with_timezone(tidspunkt)
            })
    }

    override suspend fun hentNotifikasjonerSistLest(fnr: String): OffsetDateTime? {
        val result = database.nonTransactionalExecuteQuery(
            """
                select tidspunkt from notifikasjoner_sist_lest
                where fnr = ?
            """.trimIndent(), {
                text(fnr)
            },
            {
                getObject("tidspunkt", OffsetDateTime::class.java)
            }
        )
        return result.firstOrNull()
    }

    private suspend fun oppdaterModellEtterDelete(
        aggregateId: UUID,
        grupperingsid: String?,
        merkelapp: String?
    ) {
        database.transaction({
            throw RuntimeException("Delete", it)
        }) {
            if (grupperingsid != null && merkelapp != null) {
                // cascade hard delete av sak med grupperingsid og merkelapp
                executeUpdate("""delete from notifikasjon n where n.grupperingsid = ? and merkelapp = ?;""") {
                    text(grupperingsid)
                    text(merkelapp)
                }
            }
            executeUpdate("""delete from notifikasjon where id = ?;""") {
                uuid(aggregateId)
            }

            executeUpdate("""delete from brukerklikk where notifikasjonsid = ?;""") {
                uuid(aggregateId)
            }

            executeUpdate("""delete from sak where id = ?;""") {
                uuid(aggregateId)
            }

            registrerDelete(this, aggregateId)
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtført(utførtHendelse: OppgaveUtført, metdata: HendelseMetadata) {
        val utførtTidspunkt = utførtHendelse.utfoertTidspunkt ?: metdata.timestamp.atOffset(ZoneOffset.UTC)
        database.transaction {
            executeUpdate(
                """
                UPDATE notifikasjon
                SET 
                tilstand = '${ProdusentModel.Oppgave.Tilstand.UTFOERT}',
                utfoert_tidspunkt = ?,
                lenke = coalesce(?, lenke)
                WHERE id = ?
            """
            ) {
                timestamp_with_timezone(utførtTidspunkt)
                nullableText(utførtHendelse.nyLenke)
                uuid(utførtHendelse.notifikasjonId)
            }
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtgått(utgåttHendelse: OppgaveUtgått) {
        database.transaction {
            executeUpdate(
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
            settSakOppdatert(
                hentSakIdByNotifikasjonsId(beskjedOpprettet.notifikasjonId),
                beskjedOpprettet.opprettetTidspunkt
            )
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
                    id, virksomhetsnummer, tittel, lenke, merkelapp, grupperingsid, neste_steg, tilleggsinformasjon, sist_endret_tidspunkt, opprettet_tidspunkt
                )
                values (?, ?, ? ,?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing;
            """
            ) {
                uuid(sakOpprettet.sakId)
                text(sakOpprettet.virksomhetsnummer)
                text(sakOpprettet.tittel)
                nullableText(sakOpprettet.lenke)
                text(sakOpprettet.merkelapp)
                text(sakOpprettet.grupperingsid)
                nullableText(sakOpprettet.nesteSteg)
                nullableText(sakOpprettet.tilleggsinformasjon)
                instantAsText(sakOpprettet.opprettetTidspunkt(hendelseMetadata.timestamp))
                instantAsText(sakOpprettet.opprettetTidspunkt(hendelseMetadata.timestamp))
            }


            /** Slettes etter migrering er kjørt og feltene har fått "not null"-constraint.
             *  NB. `greatest('foo', null) = 'foo'`, og `least('foo', null) = 'foo'`.
             **/
            executeUpdate(
                """
                update sak
                set 
                    sist_endret_tidspunkt = greatest(sist_endret_tidspunkt, ?),
                    opprettet_tidspunkt = least(opprettet_tidspunkt, ?)
                where 
                    id = ?
            """
            ) {
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
                    id, sak_id, status, overstyrt_statustekst, mottatt_tidspunkt, oppgitt_tidspunkt 
                )
                values (?, ?, ?, ?, ?, ?)
                on conflict (id) do update 
                set 
                    status = excluded.status,
                    overstyrt_statustekst = excluded.overstyrt_statustekst,
                    mottatt_tidspunkt = excluded.mottatt_tidspunkt,
                    oppgitt_tidspunkt = excluded.oppgitt_tidspunkt
            """
            ) {
                uuid(nyStatusSak.hendelseId)
                uuid(nyStatusSak.sakId)
                text(nyStatusSak.status.name)
                nullableText(nyStatusSak.overstyrStatustekstMed)
                timestamp_with_timezone(nyStatusSak.mottattTidspunkt)
                nullableTimestamptz(nyStatusSak.oppgittTidspunkt)
            }

            settSakOppdatert(nyStatusSak.sakId, nyStatusSak.oppgittTidspunkt ?: nyStatusSak.mottattTidspunkt)

            executeUpdate(
                """
                with sak_status_agg as (
                    select s.id, s.tittel || ' ' || s.merkelapp AS tittel_merkelapp, string_agg(ss.status || ' ' || ss.overstyrt_statustekst, ' ') AS statustekst
                    from sak s
                        left outer join sak_status ss on s.id = ss.sak_id
                    where s.id = ?
                    group by s.id, s.tittel, s.merkelapp
                )
                update sak_search set text = lower(sak_status_agg.tittel_merkelapp) || ' ' || lower(coalesce(sak_status_agg.statustekst, ''))
                from sak_status_agg
                where sak_search.id = sak_status_agg.id
            """
            ) {
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

    private suspend fun oppdaterModellEtterTilleggsinformasjonSak(tilleggsinformasjonSak: TilleggsinformasjonSak) {
        database.transaction {
            executeUpdate(
                """
                    update sak
                    set tilleggsinformasjon = ?
                    where id = ?
                """
            ) {
                nullableText(tilleggsinformasjonSak.tilleggsinformasjon)
                uuid(tilleggsinformasjonSak.sakId)
            }
        }
    }

    private suspend fun oppdaterModellEtterNesteStegSak(nesteStegSak: NesteStegSak) {
        database.transaction {
            executeUpdate(
                """
                    update sak
                    set neste_steg = ?
                    where id = ?
                """
            ) {
                nullableText(nesteStegSak.nesteSteg)
                uuid(nesteStegSak.sakId)
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

            settSakOppdatert(
                hentSakIdByNotifikasjonsId(påminnelseOpprettet.notifikasjonId),
                påminnelseOpprettet.tidspunkt.påminnelseTidspunkt
            )
        }
    }

    private fun Transaction.storeMottaker(notifikasjonId: UUID?, sakId: UUID?, mottaker: Mottaker) {
        when (mottaker) {
            is NærmesteLederMottaker -> storeNærmesteLederMottaker(
                notifikasjonId = notifikasjonId,
                sakId = sakId,
                mottaker = mottaker
            )

            is AltinnMottaker -> {
                storeAltinnMottaker(
                    notifikasjonId = notifikasjonId,
                    sakId = sakId,
                    orgNr = mottaker.virksomhetsnummer,
                    altinnTilgang = "${mottaker.serviceCode}:${mottaker.serviceEdition}"
                )
            }

            is HendelseModel.AltinnRessursMottaker -> {
                storeAltinnMottaker(
                    notifikasjonId = notifikasjonId,
                    sakId = sakId,
                    orgNr = mottaker.virksomhetsnummer,
                    altinnTilgang = mottaker.ressursId
                )
            }
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
        orgNr: String,
        altinnTilgang: String
    ) {
        executeUpdate(
            """
            insert into mottaker_altinn_tilgang
                (notifikasjon_id, sak_id, virksomhet, altinn_tilgang)
            values (?, ?, ?, ?)
            on conflict do nothing
        """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            text(orgNr)
            text(altinnTilgang)
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

            settSakOppdatert(
                hentSakIdByNotifikasjonsId(oppgaveOpprettet.notifikasjonId),
                oppgaveOpprettet.opprettetTidspunkt
            )

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

    override suspend fun hentSakstyper(fnr: String, altinnTilganger: AltinnTilganger): List<String> {
        return timer.coRecord {

            val rows = database.nonTransactionalExecuteQuery(
                """
                    with 
                        mine_altinntilganger as (
                            select * from json_to_recordset(?::json)  
                            as ("orgNr" text, tilgang text)
                        ),
                        mine_altinn_saker as (
                            select er.sak_id
                            from mottaker_altinn_tilgang er
                            join mine_altinntilganger at on 
                                er.virksomhet = at."orgNr" and
                                er.altinn_tilgang = at.tilgang
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
                    jsonb(altinnTilganger.tilganger)
                    text(fnr)
                }
            ) {
                getString("merkelapp")
            }
            return@coRecord rows
        }
    }

    private suspend fun oppdaterModellEtterFristUtsatt(hendelse: FristUtsatt) {
        database.transaction {
            executeUpdate(
                """
            update notifikasjon
            set tilstand = '${BrukerModel.Oppgave.Tilstand.NY}',
                frist = ?,
                utgaatt_tidspunkt = null,
                paaminnelse_tidspunkt = null
            where
                id = ? and tilstand <> '${BrukerModel.Oppgave.Tilstand.UTFOERT}'
        """
            ) {
                date(hendelse.frist)
                uuid(hendelse.notifikasjonId)
            }
        }
    }

    private suspend fun oppdaterModellEtterKalenderavtaleOpprettet(hendelse: HendelseModel.KalenderavtaleOpprettet) {
        database.transaction {
            executeUpdate(
                """
                insert into notifikasjon(
                    id,
                    type,
                    tilstand,
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
                values (?, 'KALENDERAVTALE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                on conflict do nothing;
            """
            ) {
                with(hendelse) {
                    uuid(notifikasjonId)
                    text(tilstand.name)
                    text(merkelapp)
                    text(tekst)
                    text(grupperingsid)
                    text(lenke)
                    text(eksternId)
                    timestamp_with_timezone(opprettetTidspunkt)
                    text(virksomhetsnummer)
                    localDateTimeAsText(startTidspunkt)
                    nullableLocalDateTimeAsText(sluttTidspunkt)
                    nullableJsonb(lokasjon)
                    nullableBoolean(erDigitalt)
                }
            }

            settSakOppdatert(hendelse.sakId, hendelse.opprettetTidspunkt)

            for (mottaker in hendelse.mottakere) {
                storeMottaker(
                    notifikasjonId = hendelse.notifikasjonId,
                    sakId = null,
                    mottaker
                )
            }
        }
    }

    private suspend fun oppdaterModellEtterKalenderavtaleOppdatert(hendelse: HendelseModel.KalenderavtaleOppdatert) {
        database.transaction {
            executeUpdate(
                """
                update notifikasjon set
                    tilstand = coalesce(?, tilstand),
                    lenke = coalesce(?, lenke),
                    tekst = coalesce(?, tekst),
                    start_tidspunkt = coalesce(?, start_tidspunkt),
                    slutt_tidspunkt = coalesce(?, slutt_tidspunkt),
                    lokasjon = coalesce(?::jsonb, lokasjon),
                    digitalt = coalesce(?, digitalt)
                where id = ?;
            """
            ) {
                with(hendelse) {
                    nullableText(tilstand?.name)
                    nullableText(lenke)
                    nullableText(tekst)
                    nullableLocalDateTimeAsText(startTidspunkt)
                    nullableLocalDateTimeAsText(sluttTidspunkt)
                    nullableJsonb(lokasjon)
                    nullableBoolean(erDigitalt)
                    uuid(notifikasjonId)
                }
            }
        }
    }

    private fun Transaction.hentSakIdByNotifikasjonsId(notifikasjonsId: UUID): UUID? {
        val sakId = executeQuery(
            """
                select s.id as id
                from sak as s 
                inner join notifikasjon as n on s.merkelapp = n.merkelapp and s.grupperingsid = n.grupperingsid 
                where n.id = ?
            """.trimMargin(),
            {
                uuid(notifikasjonsId)
            }, {
                getUuid("id")
            }
        )
        return sakId.firstOrNull()
    }

    private fun Transaction.settSakOppdatert(sakId: UUID?, tidspunkt: OffsetDateTime) =
        settSakOppdatert(sakId, tidspunkt.toInstant())

    private fun Transaction.settSakOppdatert(sakId: UUID?, tidspunkt: Instant) {
        if (sakId === null)
            return

        executeUpdate(
            """
                update sak set
                sist_endret_tidspunkt = ?
                where id = ?
                and sist_endret_tidspunkt < ?
            """.trimIndent(),
            {
                instantAsText(tidspunkt)
                uuid(sakId)
                instantAsText(tidspunkt)
            }
        )
    }
}