package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.sql.ResultSet
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

    override suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon? {
        return database.transaction {
            val entityCache = mutableMapOf<UUID, ProdusentModel.Notifikasjon>()
            executeQuery(
                """ 
                select notifikasjon.*, eksterntvarsel.* from notifikasjon 
                left join eksternt_varsel eksterntvarsel on notifikasjon.id = eksterntvarsel.notifikasjon_id
                where notifikasjon.id = ?
                """,
                {
                    uuid(id)
                },
                {
                    val notifikasjon = entityCache.getOrPut(id) {
                        resultSetTilNotifikasjon()
                    }

                    getObject("varsel_id", UUID::class.java)?.let { varselId ->
                        val eksterntVarsel = ProdusentModel.EksterntVarsel(
                            varselId = varselId,
                            status = ProdusentModel.EksterntVarsel.Status.valueOf(getString("status")),
                            feilmelding = getString("feilmelding")
                        )
                        entityCache[id] = notifikasjon.medEksterntVarsel(eksterntVarsel)

                    }
                    entityCache[id]!!
                }
            ).lastDistinctBy { it.id }.firstOrNull()
        }
    }

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? {
        return database.transaction {
            val entityCache = mutableMapOf<UUID, ProdusentModel.Notifikasjon>()
            executeQuery(
                """ 
                select notifikasjon.*, eksterntvarsel.* from notifikasjon 
                left join eksternt_varsel eksterntvarsel on notifikasjon.id = eksterntvarsel.notifikasjon_id
                where ekstern_id = ? and merkelapp = ? 
                """, {
                    string(eksternId)
                    string(merkelapp)
                }, {
                    val id = getObject("id", UUID::class.java)
                    val notifikasjon = entityCache.getOrPut(id) {
                        resultSetTilNotifikasjon()
                    }

                    getObject("varsel_id", UUID::class.java)?.let { varselId ->
                        val eksterntVarsel = ProdusentModel.EksterntVarsel(
                            varselId = varselId,
                            status = ProdusentModel.EksterntVarsel.Status.valueOf(getString("status")),
                            feilmelding = getString("feilmelding")
                        )
                        entityCache[id] = notifikasjon.medEksterntVarsel(eksterntVarsel)

                    }
                    entityCache[id]!!
                }
            ).lastDistinctBy { it.id }.firstOrNull()
        }
    }

    private fun ResultSet.resultSetTilNotifikasjon(): ProdusentModel.Notifikasjon =
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
                eksterneVarsler = listOf(),
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
                eksterneVarsler = listOf(),
            )
            else ->
                throw Exception("Ukjent notifikasjonstype '$type'")
        }

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
    ): List<ProdusentModel.Notifikasjon> {
        val entityCache = mutableMapOf<UUID, ProdusentModel.Notifikasjon>()
        return database.nonTransactionalExecuteQuery(
            """ select notifikasjon.*, eksterntvarsel.* from notifikasjon 
                  left join eksternt_varsel eksterntvarsel on notifikasjon.id = eksterntvarsel.notifikasjon_id
                  where merkelapp = any(?)
                  ${grupperingsid?.let { "and grupperingsid = ?" } ?: ""} 
                  limit ?
                  offset ?
            """.trimMargin(), {
                stringList(merkelapper)
                grupperingsid?.let { string(grupperingsid) }
                integer(antall)
                integer(offset)
            },
            {
                val id = getObject("id", UUID::class.java)
                val notifikasjon = entityCache.getOrPut(id) {
                    resultSetTilNotifikasjon()
                }

                getObject("varsel_id", UUID::class.java)?.let { varselId ->
                    val eksterntVarsel = ProdusentModel.EksterntVarsel(
                        varselId = varselId,
                        status = ProdusentModel.EksterntVarsel.Status.valueOf(getString("status")),
                        feilmelding = getString("feilmelding")
                    )
                    entityCache[id] = notifikasjon.medEksterntVarsel(eksterntVarsel)

                }
                entityCache[id]!!
            }
        ).lastDistinctBy(ProdusentModel.Notifikasjon::id)
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
        database.nonTransactionalExecuteUpdate(
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
            values ('BESKJED', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json)
            on conflict on constraint notifikasjon_pkey do nothing;
        """
        ) {
            val nyBeskjed = beskjedOpprettet.tilProdusentModel()
            uuid(nyBeskjed.id)
            string(nyBeskjed.merkelapp)
            string(nyBeskjed.tekst)
            nullableString(nyBeskjed.grupperingsid)
            string(nyBeskjed.lenke)
            string(nyBeskjed.eksternId)
            timestamptz(nyBeskjed.opprettetTidspunkt)
            string(objectMapper.writeValueAsString(nyBeskjed.mottaker))
        }
        database.nonTransactionalExecuteBatch(
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

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: Hendelse.OppgaveOpprettet) {
        database.nonTransactionalExecuteUpdate(
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
            val nyBeskjed = oppgaveOpprettet.tilProdusentModel()
            uuid(nyBeskjed.id)
            string(nyBeskjed.merkelapp)
            string(nyBeskjed.tekst)
            nullableString(nyBeskjed.grupperingsid)
            string(nyBeskjed.lenke)
            string(nyBeskjed.eksternId)
            timestamptz(nyBeskjed.opprettetTidspunkt)
            string(objectMapper.writeValueAsString(nyBeskjed.mottaker))
        }
        database.nonTransactionalExecuteBatch(
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
}