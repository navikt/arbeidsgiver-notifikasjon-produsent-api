package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
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
}

class ProdusentRepositoryImpl(
    private val database: Database,
) : ProdusentRepository {
    val log = logger()

    override suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon? =
        hentNotifikasjonerMedVarsler(
            """ 
                where notifikasjon.id = ?
            """
        ) {
            uuid(id)
        }
            .firstOrNull()

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? =
        hentNotifikasjonerMedVarsler(
            """ 
                where ekstern_id = ? and merkelapp = ? 
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
                where merkelapp = any(?)
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

    private suspend fun hentNotifikasjonerMedVarsler(
        filter: String,
        setup: ParameterSetters.() -> Unit
    ): List<ProdusentModel.Notifikasjon> =
        database.nonTransactionalExecuteQuery(""" 
            with 
                valgt_notifikasjon as (
                    select notifikasjon.* 
                    from notifikasjon
                    $filter
                )
            select 
                valgt_notifikasjon.*, 
                coalesce(ev.eksterne_varsler_json, '[]'::json) as eksterne_varsler,
                (coalesce(ma.mottakere::jsonb, '[]'::jsonb) || coalesce(md.mottakere::jsonb, '[]'::jsonb)) as mottakere
            from valgt_notifikasjon
            left join eksterne_varsler_json ev 
                on ev.notifikasjon_id = valgt_notifikasjon.id
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
                    mottakere = objectMapper.readValue(getString("mottakere")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                    eksterneVarsler = objectMapper.readValue(getString("eksterne_varsler")),
                )
                "OPPGAVE" -> ProdusentModel.Oppgave(
                    merkelapp = getString("merkelapp"),
                    tilstand = ProdusentModel.Oppgave.Tilstand.valueOf(getString("tilstand")),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottakere = objectMapper.readValue(getString("mottakere")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                    eksterneVarsler = objectMapper.readValue(getString("eksterne_varsler")),
                    )
                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }
//            .groupBy { it.id }
//            .values
//            .map { it.reduce(ProdusentModel.Notifikasjon::mergeEksterneVarsler) }

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        val ignored: Unit = when (hendelse) {
            is Hendelse.BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is Hendelse.OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is Hendelse.OppgaveUtført -> oppdatertModellEtterOppgaveUtført(hendelse)
            is Hendelse.BrukerKlikket -> /* Ignorer */ Unit
            is Hendelse.SoftDelete -> oppdaterModellEtterSoftDelete(hendelse)
            is Hendelse.HardDelete -> oppdaterModellEtterHardDelete(hendelse)
            is Hendelse.EksterntVarselVellykket -> oppdaterModellEtterEksterntVarselVellykket(hendelse)
            is Hendelse.EksterntVarselFeilet -> oppdaterModellEtterEksterntVarselFeilet(hendelse)
        }
    }

    private suspend fun oppdaterModellEtterHardDelete(hardDelete: Hendelse.HardDelete) {
        database.nonTransactionalExecuteUpdate(
            """
            DELETE FROM notifikasjon 
            WHERE id = ?
            """
        ) {
            uuid(hardDelete.notifikasjonId)
        }
    }

    private suspend fun oppdaterModellEtterSoftDelete(softDelete: Hendelse.SoftDelete) {
        database.nonTransactionalExecuteUpdate(
            """
            UPDATE notifikasjon
            SET deleted_at = ? 
            WHERE id = ?
            """
        ) {
            timestamptz(softDelete.deletedAt)
            uuid(softDelete.notifikasjonId)
        }
    }

    private suspend fun oppdatertModellEtterOppgaveUtført(utførtHendelse: Hendelse.OppgaveUtført) {
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

    private suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: Hendelse.BeskjedOpprettet) {
        database.transaction {
            executeUpdate("""
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
                    mottaker,
                    virksomhetsnummer
                )
                values ('BESKJED', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json, ?)
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
                string(objectMapper.writeValueAsString(beskjedOpprettet.mottaker))
                string(beskjedOpprettet.virksomhetsnummer)
            }

            for (mottaker in beskjedOpprettet.mottakere) {
                storeMottaker(beskjedOpprettet.notifikasjonId, mottaker)
            }

            executeBatch("""
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

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: Hendelse.OppgaveOpprettet) {
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
                    mottaker,
                    virksomhetsnummer
                )
                values ('OPPGAVE', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json, ?)
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
                string(objectMapper.writeValueAsString(oppgaveOpprettet.mottaker))
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

    private suspend fun oppdaterModellEtterEksterntVarselVellykket(eksterntVarselVellykket: Hendelse.EksterntVarselVellykket) {
        database.nonTransactionalExecuteUpdate(
            """
            update eksternt_varsel set status = 'SENDT' where varsel_id = ?
            """
        ) {
            uuid(eksterntVarselVellykket.varselId)
        }
    }

    private suspend fun oppdaterModellEtterEksterntVarselFeilet(eksterntVarselFeilet: Hendelse.EksterntVarselFeilet) {
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
        when (mottaker) {
            is NærmesteLederMottaker -> storeNærmesteLederMottaker(notifikasjonId, mottaker)
            is AltinnMottaker -> storeAltinnMottaker(notifikasjonId, mottaker)
        }
    }

    private fun Transaction.storeNærmesteLederMottaker(notifikasjonId: UUID, mottaker: NærmesteLederMottaker) {
        executeUpdate("""
            insert into mottaker_digisyfo(notifikasjon_id, virksomhet, fnr_leder, fnr_sykmeldt)
            values (?, ?, ?, ?)
        """) {
            uuid(notifikasjonId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.naermesteLederFnr)
            string(mottaker.ansattFnr)
        }
    }

    private fun Transaction.storeAltinnMottaker(notifikasjonId: UUID, mottaker: AltinnMottaker) {
        executeUpdate("""
            insert into mottaker_altinn_enkeltrettighet
                (notifikasjon_id, virksomhet, service_code, service_edition)
            values (?, ?, ?, ?)
        """) {
            uuid(notifikasjonId)
            string(mottaker.virksomhetsnummer)
            string(mottaker.serviceCode)
            string(mottaker.serviceEdition)
        }
    }
}