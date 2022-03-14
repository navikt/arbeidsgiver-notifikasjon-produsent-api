package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnReporteeMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.AltinnRolleMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.SoftDelete
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleRepository
import no.nav.arbeidsgiver.notifikasjon.altinn_roller.AltinnRolleRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import java.util.*

interface ProdusentRepository {
    suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon?
    suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon?
    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse)
    suspend fun finnNotifikasjoner(
        merkelapper: List<String>,
        grupperingsid: String?,
        antall: Int,
        offset: Int,
    ): List<ProdusentModel.Notifikasjon>
    suspend fun hentSak(grupperingsid: String, merkelapp: String): ProdusentModel.Sak?
    suspend fun hentSak(id: UUID): ProdusentModel.Sak?

    val altinnRolle : AltinnRolleRepository
}

class ProdusentRepositoryImpl(
    private val database: Database,
) : ProdusentRepository {
    val log = logger()

    override val altinnRolle: AltinnRolleRepository = AltinnRolleRepositoryImpl(database)

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
            string(eksternId)
            string(merkelapp)
        }
            .firstOrNull()

    override suspend fun finnNotifikasjoner(
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
            stringList(merkelapper)
            grupperingsid?.let { string(grupperingsid) }
            integer(antall)
            integer(offset)
        }

    override suspend fun hentSak(grupperingsid: String, merkelapp: String): ProdusentModel.Sak? {
        return hentSaker(
            where = """
               grupperingsid = ? and merkelapp = ?
            """,
            variables = {
                string(grupperingsid)
                string(merkelapp)
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
                (coalesce(ma.mottakere::jsonb, '[]'::jsonb) || coalesce(mar.mottakere::jsonb, '[]'::jsonb) || coalesce(md.mottakere::jsonb, '[]'::jsonb) || coalesce(maro.mottakere::jsonb, '[]'::jsonb)) as mottakere
            from valgt_notifikasjon
            left join eksterne_varsler_json ev 
                on ev.notifikasjon_id = valgt_notifikasjon.id
            left join mottakere_altinn_enkeltrettighet_json ma
                on ma.notifikasjon_id = valgt_notifikasjon.id
            left join mottakere_altinn_reportee_json mar
                on mar.notifikasjon_id = valgt_notifikasjon.id
            left join mottakere_digisyfo_json md
                on md.notifikasjon_id = valgt_notifikasjon.id
            left join mottaker_altinn_rolle_json maro
                on maro.notifikasjon_id = valgt_notifikasjon.id
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
                )
                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        val ignored: Unit = when (hendelse) {
            is SakOpprettet -> oppdaterModellEtterSakOpprettet(hendelse)
            is NyStatusSak -> oppdaterModellEtterNyStatusSak(hendelse)
            is BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is OppgaveUtført -> oppdatertModellEtterOppgaveUtført(hendelse)
            is BrukerKlikket -> /* Ignorer */ Unit
            is SoftDelete -> oppdaterModellEtterSoftDelete(hendelse)
            is HardDelete -> oppdaterModellEtterHardDelete(hendelse)
            is EksterntVarselVellykket -> oppdaterModellEtterEksterntVarselVellykket(hendelse)
            is EksterntVarselFeilet -> oppdaterModellEtterEksterntVarselFeilet(hendelse)
        }
    }

    private suspend fun oppdaterModellEtterSakOpprettet(sakOpprettet: SakOpprettet) {
        database.transaction {
            executeUpdate("""
                    insert into sak(id, merkelapp, grupperingsid, virksomhetsnummer, mottakere, tittel, lenke, tidspunkt_mottatt)
                    values (?, ?, ?, ?, ?::jsonb, ?, ?, now())
                    on conflict on constraint grupperingsid_unique do nothing
                """
            ) {
                uuid(sakOpprettet.sakId)
                string(sakOpprettet.merkelapp)
                string(sakOpprettet.grupperingsid)
                string(sakOpprettet.virksomhetsnummer)
                jsonb(sakOpprettet.mottakere)
                string(sakOpprettet.tittel)
                string(sakOpprettet.lenke)
            }

            executeUpdate("""
                insert into sak_id (incoming_sak_id, sak_id) values (?, ?)
                on conflict on constraint sak_id_pkey do nothing
            """) {
                uuid(sakOpprettet.sakId)
                uuid(sakOpprettet.sakId)
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
            val sakId = finnDbSakId(nyStatusSak.sakId)

            if (sakId == null) {
                // log? metric?
                return@transaction
            }

            executeUpdate("""
                insert into sak_status
                (id, idempotence_key, sak_id, status, overstyr_statustekst_med, tidspunkt_oppgitt, tidspunkt_mottatt)
                values (?, ?, ?, ?, ?, ?, ?)
            """) {
                uuid(nyStatusSak.hendelseId)
                string(nyStatusSak.idempotensKey)
                uuid(sakId)
                string(nyStatusSak.status.name)
                nullableString(nyStatusSak.overstyrStatustekstMed)
                nullableTimestamptz(nyStatusSak.oppgittTidspunkt)
                timestamptz(nyStatusSak.mottattTidspunkt)
            }
        }
    }


    private suspend fun oppdaterModellEtterHardDelete(hardDelete: HardDelete) {
        database.nonTransactionalExecuteUpdate(
            """
            DELETE FROM notifikasjon 
            WHERE id = ?
            """
        ) {
            uuid(hardDelete.aggregateId)
        }
    }

    private suspend fun oppdaterModellEtterSoftDelete(softDelete: SoftDelete) {
        database.nonTransactionalExecuteUpdate(
            """
            UPDATE notifikasjon
            SET deleted_at = ? 
            WHERE id = ?
            """
        ) {
            timestamptz(softDelete.deletedAt)
            uuid(softDelete.aggregateId)
        }
    }

    private suspend fun oppdatertModellEtterOppgaveUtført(utførtHendelse: OppgaveUtført) {
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
                on conflict on constraint notifikasjon_pkey do nothing;
            """
            ) {
                uuid(beskjedOpprettet.notifikasjonId)
                string(beskjedOpprettet.merkelapp)
                string(beskjedOpprettet.tekst)
                nullableString(beskjedOpprettet.grupperingsid)
                string(beskjedOpprettet.lenke)
                string(beskjedOpprettet.eksternId)
                timestamptz(beskjedOpprettet.opprettetTidspunkt)
                string(beskjedOpprettet.virksomhetsnummer)
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
                on conflict on constraint eksternt_varsel_pkey do nothing;
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
                    virksomhetsnummer
                )
                values ('OPPGAVE', 'NY', ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict on constraint notifikasjon_pkey do nothing;
            """
            ) {
                uuid(oppgaveOpprettet.notifikasjonId)
                string(oppgaveOpprettet.merkelapp)
                string(oppgaveOpprettet.tekst)
                nullableString(oppgaveOpprettet.grupperingsid)
                string(oppgaveOpprettet.lenke)
                string(oppgaveOpprettet.eksternId)
                timestamptz(oppgaveOpprettet.opprettetTidspunkt)
                string(oppgaveOpprettet.virksomhetsnummer)
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
                on conflict on constraint eksternt_varsel_pkey do nothing;
                """,
                oppgaveOpprettet.eksterneVarsler
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
            string(eksterntVarselFeilet.feilmelding)
            uuid(eksterntVarselFeilet.varselId)
        }
    }

    private fun Transaction.storeMottaker(notifikasjonId: UUID, mottaker: Mottaker) {
        val ignored = when (mottaker) {
            is NærmesteLederMottaker -> storeNærmesteLederMottaker(notifikasjonId, mottaker)
            is AltinnMottaker -> storeAltinnMottaker(notifikasjonId, mottaker)
            is AltinnReporteeMottaker -> storeAltinnReporteeMottaker(notifikasjonId, mottaker)
            is AltinnRolleMottaker -> storeAltinnRolleMottaker(notifikasjonId, mottaker)
        }
    }

    private fun Transaction.storeNærmesteLederMottaker(notifikasjonId: UUID, mottaker: NærmesteLederMottaker) {
        executeUpdate(
            """
            insert into mottaker_digisyfo(notifikasjon_id, virksomhet, fnr_leder, fnr_sykmeldt)
            values (?, ?, ?, ?)
        """
        ) {
            uuid(notifikasjonId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.naermesteLederFnr)
            string(mottaker.ansattFnr)
        }
    }

    private fun Transaction.storeAltinnMottaker(notifikasjonId: UUID, mottaker: AltinnMottaker) {
        executeUpdate(
            """
            insert into mottaker_altinn_enkeltrettighet
                (notifikasjon_id, virksomhet, service_code, service_edition)
            values (?, ?, ?, ?)
        """
        ) {
            uuid(notifikasjonId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.serviceCode)
            string(mottaker.serviceEdition)
        }
    }

    private fun Transaction.storeAltinnReporteeMottaker(notifikasjonId: UUID, mottaker: AltinnReporteeMottaker) {
        executeUpdate(
            """
            insert into mottaker_altinn_reportee
                (notifikasjon_id, virksomhet, fnr)
            values (?, ?, ?)
        """
        ) {
            uuid(notifikasjonId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.fnr)
        }
    }

    private fun Transaction.storeAltinnRolleMottaker(notifikasjonId: UUID, mottaker: AltinnRolleMottaker) {
        executeUpdate(
            """
            insert into mottaker_altinn_rolle
                (notifikasjon_id, virksomhet, role_definition_code, role_definition_id)
            values (?, ?, ?, ?)
        """
        ) {
            uuid(notifikasjonId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.roleDefinitionCode)
            string(mottaker.roleDefinitionId)
        }
    }
}