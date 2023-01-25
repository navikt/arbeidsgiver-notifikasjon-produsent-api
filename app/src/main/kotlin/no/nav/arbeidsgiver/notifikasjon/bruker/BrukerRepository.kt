package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleRepository
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel.Tilganger
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnReporteeMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRolleMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Hendelse
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
    ): HentSakerResultat

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse)
    suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String?
    suspend fun oppdaterModellEtterNærmesteLederLeesah(nærmesteLederLeesah: NarmesteLederLeesah)

    val altinnRolle: AltinnRolleRepository
}

class BrukerRepositoryImpl(
    private val database: Database
) : BrukerRepository {
    private val log = logger()
    private val timer = Metrics.meterRegistry.timer("query_model_repository_hent_notifikasjoner")
    override val altinnRolle: AltinnRolleRepository = AltinnRolleRepositoryImpl(database)

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
        val tilgangerAltinnReporteeMottaker = tilganger.reportee.map {
            AltinnReporteeMottaker(
                virksomhetsnummer = it.virksomhet,
                fnr = it.fnr
            )
        }
        val tilgangerAltinnRolleMottaker = tilganger.rolle.map {
            AltinnRolleMottaker(
                virksomhetsnummer = it.virksomhet,
                roleDefinitionId = it.roleDefinitionId,
                roleDefinitionCode = it.roleDefinitionCode,
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
                mine_altinnreporteetilganger as (
                    select * from json_to_recordset(?::json) 
                    as (virksomhetsnummer text, "fnr" text)
                ),
                mine_altinnrolletilganger as (
                    select * from json_to_recordset(?::json) 
                    as (virksomhetsnummer text, "roleDefinitionId" text, "roleDefinitionCode" text)
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
                mine_altinn_reportee_notifikasjoner as (
                    select rep.notifikasjon_id
                    from mottaker_altinn_reportee rep
                    join mine_altinnreporteetilganger at on 
                        rep.virksomhet = at.virksomhetsnummer and
                        rep.fnr = at."fnr"
                    where
                        rep.notifikasjon_id is not null
                ),
                mine_altinn_rolle_notifikasjoner as (
                    select rol.notifikasjon_id
                    from mottaker_altinn_rolle rol
                    join mine_altinnrolletilganger at on 
                        rol.virksomhet = at.virksomhetsnummer and
                        rol.role_definition_id = at."roleDefinitionId" and
                        rol.role_definition_code = at."roleDefinitionCode"
                    where
                        rol.notifikasjon_id is not null
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
                    union 
                    (select * from mine_altinn_reportee_notifikasjoner)
                    union 
                    (select * from mine_altinn_rolle_notifikasjoner)
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
                coalesce(paaminnelse_tidspunkt, opprettet_tidspunkt) desc
            limit 200
            """,
            {
                jsonb(tilgangerAltinnMottaker)
                jsonb(tilgangerAltinnReporteeMottaker)
                jsonb(tilgangerAltinnRolleMottaker)
                string(fnr)
                string(fnr)
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
                    id = getObject("id", UUID::class.java),
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
                    paaminnelseTidspunkt = getObject("paaminnelse_tidspunkt", OffsetDateTime::class.java),
                    frist = getObject("frist", LocalDate::class.java),
                    id = getObject("id", UUID::class.java),
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
    ): BrukerRepository.HentSakerResultat {
        return timer.coRecord {
            val tilgangerAltinnMottaker = tilganger.tjenestetilganger.map {
                AltinnMottaker(
                    serviceCode = it.servicecode,
                    serviceEdition = it.serviceedition,
                    virksomhetsnummer = it.virksomhet
                )
            }.filter {  virksomhetsnummer.contains(it.virksomhetsnummer) }
            val tilgangerAltinnReporteeMottaker = tilganger.reportee.map {
                AltinnReporteeMottaker(
                    virksomhetsnummer = it.virksomhet,
                    fnr = it.fnr
                )
            }.filter { virksomhetsnummer.contains(it.virksomhetsnummer) }
            val tilgangerAltinnRolleMottaker = tilganger.rolle.map {
                AltinnRolleMottaker(
                    virksomhetsnummer = it.virksomhet,
                    roleDefinitionId = it.roleDefinitionId,
                    roleDefinitionCode = it.roleDefinitionCode,
                )
            }.filter { virksomhetsnummer.contains(it.virksomhetsnummer) }

            var tekstsoekElementer = (tekstsoek ?: "")
                .trim()
                .lowercase()
                .split(searchQuerySplit)
                .map { it.replace("\\", "\\\\") }
                .map { it.replace("_", "\\_") }
                .map { it.replace("%", "\\%") }

            val truncateSearchTerms = 10
            if (tekstsoekElementer.size > truncateSearchTerms) {
                log.warn("{} search terms; truncating to {}.", tekstsoekElementer.size, truncateSearchTerms)
                tekstsoekElementer = tekstsoekElementer.take(truncateSearchTerms)
            }

            val tekstsoekSql = tekstsoekElementer
                .joinToString(separator = " and ") { """ search.text like '%' || ? || '%' """ }
                .ifNotBlank { "and $it" }

            val sorteringSql = when (sortering) {
                BrukerAPI.SakSortering.OPPDATERT -> "sist_endret desc"
                BrukerAPI.SakSortering.OPPRETTET -> """
                   s.statuser#>>'{-1,tidspunkt}' desc 
                """
                BrukerAPI.SakSortering.FRIST -> """
                    frist nulls last, nye_oppgaver desc, sist_endret desc
                """
            }

            val rows = database.nonTransactionalExecuteQuery(
                /*  quotes are necessary for fields from json, otherwise they are lower-cased */
                """
                    with 
                        mine_altinntilganger as (
                            select * from json_to_recordset(?::json) 
                            as (virksomhetsnummer text, "serviceCode" text, "serviceEdition" text)
                        ),
                        mine_altinnreporteetilganger as (
                            select * from json_to_recordset(?::json) 
                            as (virksomhetsnummer text, "fnr" text)
                        ),
                        mine_altinnrolletilganger as (
                            select * from json_to_recordset(?::json) 
                            as (virksomhetsnummer text, "roleDefinitionId" text, "roleDefinitionCode" text)
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
                        mine_altinn_reportee_saker as (
                            select rep.sak_id
                            from mottaker_altinn_reportee rep
                            join mine_altinnreporteetilganger at on 
                                rep.virksomhet = at.virksomhetsnummer and
                                rep.fnr = at."fnr"
                            where
                                rep.sak_id is not null
                        ),
                        mine_altinn_rolle_saker as (
                            select rol.sak_id
                            from mottaker_altinn_rolle rol
                            join mine_altinnrolletilganger at on 
                                rol.virksomhet = at.virksomhetsnummer and
                                rol.role_definition_id = at."roleDefinitionId" and
                                rol.role_definition_code = at."roleDefinitionCode"
                            where
                                rol.sak_id is not null
                        ),
                        mine_digisyfo_saker as (
                            select sak_id
                            from mottaker_digisyfo_for_fnr
                            where fnr_leder = ? and virksomhet = any(?) and sak_id is not null
                        ),
                        mine_saker as (
                            (select * from mine_digisyfo_saker)
                            union 
                            (select * from mine_altinn_saker)
                            union 
                            (select * from mine_altinn_reportee_saker)
                            union 
                            (select * from mine_altinn_rolle_saker)
                        ),
                        mine_saker_ikke_paginert as (
                            select 
                                s.id as "sakId", 
                                s.virksomhetsnummer as virksomhetsnummer,
                                s.tittel as tittel,
                                s.lenke as lenke,
                                s.merkelapp as merkelapp,
                                s.grupperingsid as grupperingsid,
                                to_jsonb(status_json.statuser) as statuser,
                                status_json.sist_endret as sist_endret
                            from mine_saker as ms
                            join sak as s on s.id = ms.sak_id
                            join sak_status_json as status_json on s.id = status_json.sak_id
                            join sak_search as search on s.id = search.id
                            where coalesce(s.merkelapp = any(?), true)
                            $tekstsoekSql
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
                        mine_altinn_reportee_notifikasjoner as (
                            select rep.notifikasjon_id
                            from mottaker_altinn_reportee rep
                            join mine_altinnreporteetilganger at on 
                                rep.virksomhet = at.virksomhetsnummer and
                                rep.fnr = at."fnr"
                            where
                                rep.notifikasjon_id is not null
                        ),
                        mine_altinn_rolle_notifikasjoner as (
                            select rol.notifikasjon_id
                            from mottaker_altinn_rolle rol
                            join mine_altinnrolletilganger at on 
                                rol.virksomhet = at.virksomhetsnummer and
                                rol.role_definition_id = at."roleDefinitionId" and
                                rol.role_definition_code = at."roleDefinitionCode"
                            where
                                rol.notifikasjon_id is not null
                        ),
                        mine_digisyfo_notifikasjoner as (
                            select notifikasjon_id 
                            from mottaker_digisyfo_for_fnr
                            where fnr_leder = ? and notifikasjon_id is not null
                        ),
                        mine_saksoppgaver as (
                            select
                                s."sakId",
                                s.virksomhetsnummer,
                                s.tittel,
                                s.lenke,
                                s.merkelapp,
                                s.grupperingsid,
                                s.statuser,
                                s.sist_endret,
                                o.oppgaver,
                                (select count(*)
                                 from unnest(o.oppgaver) as o2
                                 where  o2 ->> 'tilstand' = '${BrukerModel.Oppgave.Tilstand.NY}'
                                ) as nye_oppgaver,
                                (select o2 ->> 'frist'
                                from unnest(o.oppgaver) as o2
                                where  o2 ->> 'tilstand' = '${BrukerModel.Oppgave.Tilstand.NY}'
                                order by o2 ->> 'frist' nulls last
                                limit 1) as frist
                            from mine_saker_ikke_paginert s
                            cross join lateral (
                                select array (
                                    select jsonb_build_object( 
                                    'tilstand', n.tilstand, 
                                    'frist', n.frist, 
                                    'paaminnelseTidspunkt', n.paaminnelse_tidspunkt 
                                    )
                                    from notifikasjon n
                                    where n.grupperingsid = s.grupperingsid
                                        and n.id in (
                                            select * from mine_digisyfo_notifikasjoner
                                                union
                                            select * from mine_altinn_notifikasjoner
                                                union
                                            select * from mine_altinn_reportee_notifikasjoner
                                                union
                                            select * from mine_altinn_rolle_notifikasjoner
                                        )
                                    order by n.frist nulls last
                                ) as oppgaver
                            ) o
                            order by ${sorteringSql}
                            offset ? limit ? 
                        )
                    select
                        (select count(*) from mine_saker_ikke_paginert) as totalt_antall_saker,
                        (select coalesce(
                            jsonb_agg(jsonb_build_object(
                                'sakId', "sakId",
                                'virksomhetsnummer', virksomhetsnummer,
                                'tittel', tittel,
                                'lenke', lenke,
                                'merkelapp', merkelapp,
                                'grupperingsid', grupperingsid,
                                'statuser', statuser,
                                'sist_endret', sist_endret,
                                'oppgaver', oppgaver
                            )),  
                            '[]'::jsonb
                        ) from mine_saksoppgaver) as saker
                    """,
                {
                    jsonb(tilgangerAltinnMottaker)
                    jsonb(tilgangerAltinnReporteeMottaker)
                    jsonb(tilgangerAltinnRolleMottaker)
                    string(fnr)
                    stringList(virksomhetsnummer)
                    nullableStringList(sakstyper)
                    tekstsoekElementer.forEach { string(it) }
                    string(fnr)
                    integer(offset)
                    integer(limit)
                }
            ) {
                BrukerRepository.HentSakerResultat(
                    totaltAntallSaker = getInt("totalt_antall_saker"),
                    saker = laxObjectMapper.readValue(getString("saker")),
                )
            }
            return@coRecord rows.first()
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

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        /* when-expressions gives error when not exhaustive, as opposed to when-statement. */
        @Suppress("UNUSED_VARIABLE") val ignored: Unit = when (hendelse) {
            is SakOpprettet -> oppdaterModellEtterSakOpprettet(hendelse)
            is NyStatusSak -> oppdaterModellEtterNyStatusSak(hendelse)
            is BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is BrukerKlikket -> oppdaterModellEtterBrukerKlikket(hendelse)
            is OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is OppgaveUtført -> oppdaterModellEtterOppgaveUtført(hendelse)
            is OppgaveUtgått -> oppdaterModellEtterOppgaveUtgått(hendelse)
            is SoftDelete -> oppdaterModellEtterDelete(hendelse.aggregateId)
            is HardDelete -> oppdaterModellEtterDelete(hendelse.aggregateId)
            is EksterntVarselFeilet -> Unit
            is EksterntVarselVellykket -> Unit
            is PåminnelseOpprettet -> oppdaterModellEtterPåminnelseOpprettet(hendelse)
        }
    }

    private suspend fun oppdaterModellEtterDelete(aggregateId: UUID) {
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
        }
    }

    private suspend fun oppdaterModellEtterOppgaveUtført(utførtHendelse: OppgaveUtført) {
        database.nonTransactionalExecuteUpdate(
            """
            UPDATE notifikasjon
            SET tilstand = '${ProdusentModel.Oppgave.Tilstand.UTFOERT}'
            WHERE id = ?
        """
        ) {
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
            timestamp_with_timezone(utgåttHendelse.utgaattTidspunkt)
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
            string(brukerKlikket.fnr)
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
                string(beskjedOpprettet.merkelapp)
                string(beskjedOpprettet.tekst)
                nullableString(beskjedOpprettet.grupperingsid)
                string(beskjedOpprettet.lenke)
                string(beskjedOpprettet.eksternId)
                timestamp_with_timezone(beskjedOpprettet.opprettetTidspunkt)
                string(beskjedOpprettet.virksomhetsnummer)
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

    private suspend fun oppdaterModellEtterSakOpprettet(sakOpprettet: SakOpprettet) {
        database.transaction {
            executeUpdate(
                """
                insert into sak(
                    id, virksomhetsnummer, tittel, lenke, merkelapp, grupperingsid
                )
                values (?, ?, ? ,?, ?, ?)
                on conflict do nothing;
            """
            ) {
                uuid(sakOpprettet.sakId)
                string(sakOpprettet.virksomhetsnummer)
                string(sakOpprettet.tittel)
                string(sakOpprettet.lenke)
                string(sakOpprettet.merkelapp)
                string(sakOpprettet.grupperingsid)
            }

            executeUpdate(
                """
                insert into sak_search(id, text) values (?, lower(?)) on conflict do nothing
            """
            ) {
                uuid(sakOpprettet.sakId)
                string("${sakOpprettet.tittel} ${sakOpprettet.merkelapp}")
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
        val sakFinnes = database.nonTransactionalExecuteQuery(
            """
                    select exists(select 1 from sak where id=?) as exists
                """, {
                uuid(nyStatusSak.sakId)
            }) {
            getBoolean("exists")
        }[0]

        if (!sakFinnes) {
            log.warn("hopper over oppdaterModellEtterNyStatusSak for sak {} som ikke finnes", nyStatusSak.sakId)
            return
        }

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
                string(nyStatusSak.status.name)
                nullableString(nyStatusSak.overstyrStatustekstMed)
                timestamp_with_timezone(nyStatusSak.oppgittTidspunkt ?: nyStatusSak.mottattTidspunkt)
            }

            executeUpdate(
                """
                update sak_search
                set text =  text || ' ' || lower(?) || ' ' || lower(coalesce(?, ''))
                where id = ?
            """
            ) {
                string(nyStatusSak.status.name)
                nullableString(nyStatusSak.overstyrStatustekstMed)
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
                    string(nyStatusSak.nyLenkeTilSak)
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
            is AltinnReporteeMottaker -> storeAltinnReporteeMottaker(
                notifikasjonId = notifikasjonId,
                sakId = sakId,
                mottaker = mottaker
            )
            is AltinnRolleMottaker -> storeAltinnRolleMottaker(
                notifikasjonId = notifikasjonId,
                sakId = sakId,
                mottaker = mottaker
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
        """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.naermesteLederFnr)
            string(mottaker.ansattFnr)
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
        """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.serviceCode)
            string(mottaker.serviceEdition)
        }
    }

    private fun Transaction.storeAltinnReporteeMottaker(
        notifikasjonId: UUID?,
        sakId: UUID?,
        mottaker: AltinnReporteeMottaker
    ) {
        executeUpdate(
            """
            insert into mottaker_altinn_reportee
                (notifikasjon_id, sak_id, virksomhet, fnr)
            values (?, ?, ?, ?)
        """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.fnr)
        }
    }

    private fun Transaction.storeAltinnRolleMottaker(
        notifikasjonId: UUID?,
        sakId: UUID?,
        mottaker: AltinnRolleMottaker
    ) {
        executeUpdate(
            """
            insert into mottaker_altinn_rolle
                (notifikasjon_id, sak_id, virksomhet, role_definition_code, role_definition_id)
            values (?, ?, ?, ?, ?)
        """
        ) {
            nullableUuid(notifikasjonId)
            nullableUuid(sakId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.roleDefinitionCode)
            string(mottaker.roleDefinitionId)
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
                string(oppgaveOpprettet.merkelapp)
                string(oppgaveOpprettet.tekst)
                nullableString(oppgaveOpprettet.grupperingsid)
                string(oppgaveOpprettet.lenke)
                string(oppgaveOpprettet.eksternId)
                timestamp_with_timezone(oppgaveOpprettet.opprettetTidspunkt)
                string(oppgaveOpprettet.virksomhetsnummer)
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
                string(nærmesteLederLeesah.fnr)
                string(nærmesteLederLeesah.narmesteLederFnr)
                string(nærmesteLederLeesah.orgnummer)
            }
        }
    }
}
