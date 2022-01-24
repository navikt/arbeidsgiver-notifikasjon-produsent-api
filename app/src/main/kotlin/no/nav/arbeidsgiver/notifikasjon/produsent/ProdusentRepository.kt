package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import no.nav.arbeidsgiver.notifikasjon.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.random.Random.Default.nextLong as randomLong

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
                select notifikasjon.*, eksterntvarsel.* from notifikasjon 
                left join eksternt_varsel eksterntvarsel on notifikasjon.id = eksterntvarsel.notifikasjon_id
                where notifikasjon.id = ?
            """
        ) {
            uuid(id)
        }
            .firstOrNull()

    private suspend fun hentNotifikasjonerMedVarsler(
        sqlQuery: String,
        setup: ParameterSetters.() -> Unit
    ): List<ProdusentModel.Notifikasjon> =
        database.nonTransactionalExecuteQuery(
            sqlQuery,
            setup
        ) {
            val varselId = getObject("varsel_id", UUID::class.java)
            val eksterneVarsler = if (varselId == null)
                listOf()
            else
                listOf(
                    ProdusentModel.EksterntVarsel(
                        varselId = varselId,
                        status = ProdusentModel.EksterntVarsel.Status.valueOf(getString("status")),
                        feilmelding = getString("feilmelding")
                    )
                )

            when (val type = getString("type")) {
                "BESKJED" -> ProdusentModel.Beskjed(
                    merkelapp = getString("merkelapp"),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottaker = objectMapper.readValue(getString("mottaker")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                    eksterneVarsler = eksterneVarsler,
                )
                "OPPGAVE" -> ProdusentModel.Oppgave(
                    merkelapp = getString("merkelapp"),
                    tilstand = ProdusentModel.Oppgave.Tilstand.valueOf(getString("tilstand")),
                    tekst = getString("tekst"),
                    grupperingsid = getString("grupperingsid"),
                    lenke = getString("lenke"),
                    eksternId = getString("ekstern_id"),
                    mottaker = objectMapper.readValue(getString("mottaker")),
                    opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                    id = getObject("id", UUID::class.java),
                    deletedAt = getObject("deleted_at", OffsetDateTime::class.java),
                    eksterneVarsler = eksterneVarsler
                )
                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }
            .groupBy { it.id }
            .values
            .map { it.reduce(ProdusentModel.Notifikasjon::mergeEksterneVarsler) }

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? =
        hentNotifikasjonerMedVarsler(
            """ 
                select notifikasjon.*, eksterntvarsel.* from notifikasjon 
                left join eksternt_varsel eksterntvarsel on notifikasjon.id = eksterntvarsel.notifikasjon_id
                where ekstern_id = ? and merkelapp = ? 
            """
        ) {
            string(eksternId)
            string(merkelapp)
        }
            .firstOrNull()

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

    override suspend fun finnNotifikasjoner(
        merkelapper: List<String>,
        grupperingsid: String?,
        antall: Int,
        offset: Int,
    ): List<ProdusentModel.Notifikasjon> =
        hentNotifikasjonerMedVarsler(
            """ select notifikasjon.*, eksterntvarsel.* from notifikasjon 
                  left join eksternt_varsel eksterntvarsel on notifikasjon.id = eksterntvarsel.notifikasjon_id
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
                    mottaker
                )
                values ('BESKJED', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json)
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
                    mottaker
                )
                values ('OPPGAVE', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json)
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


    suspend fun migrate() {
        var done = false

        while (!done) {
            database.transaction {
                val (id, mottaker) = executeQuery(
                    """
                        select n.id as id, n.mottaker as mottaker
                        from notifikasjon as n
                        where
                            n.id not in (select notifikasjon_id from mottaker_altinn_enkeltrettighet)
                            and n.id not in (select notifikasjon_id from mottaker_digisyfo)
                        limit 1
                    """
                ) {
                    Pair(
                        getObject("id", UUID::class.java),
                        objectMapper.readValue<Mottaker>(getString("mottaker"))
                    )
                }
                    .singleOrNull()
                    ?: run {
                        done = true
                        return@transaction
                    }

                log.info("migrating $id")

                storeMottaker(id, mottaker)
            }
            delay(randomLong(500, 1_500))
        }
        log.info("finished copying mottakere. delete me.")
    }
}