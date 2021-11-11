package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
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
            val eksterneVarsler = executeQuery(""" select * from eksternt_varsel where notifikasjon_id = ? """, {
                uuid(id)
            }) {
                ProdusentModel.EksterntVarsel(
                    varselId = getObject("varsel_id", UUID::class.java),
                    status = ProdusentModel.EksterntVarsel.Status.valueOf(getString("status")),
                    feilmelding = getString("feilmelding")
                )
            }
            executeQuery(
                """ select * from notifikasjon where id = ? """,
                {
                    uuid(id)
                },
                {
                    resultSetTilNotifikasjon(eksterneVarsler)
                }
            ).firstOrNull()
        }
    }

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? {
        return database.transaction {
            val eksterneVarsler = executeQuery(
                """
                    select v.* from notifikasjon n
                    join eksternt_varsel v on n.notifikasjon_id = v.notifikasjon_id
                    where n.merkelapp = ? AND n.ekstern_id = ?
                """
            ) {
                ProdusentModel.EksterntVarsel(
                    varselId = getObject("varsel_id", UUID::class.java),
                    status = ProdusentModel.EksterntVarsel.Status.valueOf(getString("status")),
                    feilmelding = getString("feilmelding")
                )
            }

            executeQuery(
                """ select * from notifikasjon where ekstern_id = ? and merkelapp = ? """, {
                    string(eksternId)
                    string(merkelapp)
                }, {
                    resultSetTilNotifikasjon(eksterneVarsler)
                }
            ).firstOrNull()
        }
    }

    private fun ResultSet.resultSetTilNotifikasjon(eksterneVarsler: List<ProdusentModel.EksterntVarsel>): ProdusentModel.Notifikasjon =
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
                eksterneVarsler = eksterneVarsler,
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
            is Hendelse.EksterntVarselVellykket -> TODO()
            is Hendelse.EksterntVarselFeilet -> TODO()
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
        return database.nonTransactionalExecuteQuery(
            """ select *, eksterntvarsel.* from notifikasjon 
                  join eksternt_varsel eksterntvarsel on notifikasjon.id = eksterntvarsel.notifikasjon_id
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
            { cache ->
                val id = getObject("id", UUID::class.java)
                val notifikasjon = cache.getOrPut(id) {
                    resultSetTilNotifikasjon(mutableListOf())
                }

                getObject("eksterntvarsel.id", UUID::class.java).also { varselId ->
                    val eksterntVarsel = ProdusentModel.EksterntVarsel(
                        varselId = varselId,
                        status = ProdusentModel.EksterntVarsel.Status.valueOf(getString("eksterntvarsel.status")),
                        feilmelding = getString("eksterntvarsel.feilmelding")
                    )
                    cache[id] = when (notifikasjon) {
                        is ProdusentModel.Beskjed -> {
                            notifikasjon.copy(eksterneVarsler = notifikasjon.eksterneVarsler + listOf(eksterntVarsel))
                        }
                        is ProdusentModel.Oppgave -> {
                            notifikasjon.copy(eksterneVarsler = notifikasjon.eksterneVarsler + listOf(eksterntVarsel))
                        }
                    }

                }
                cache[id]!!
            }
        )
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
    }
}