package no.nav.arbeidsgiver.notifikasjon.produsent

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.Mottaker
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.objectMapper
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

interface ProdusentModel {

    sealed interface Notifikasjon {
        val id: UUID
        val merkelapp: String
        val deletedAt: OffsetDateTime?
    }

    data class Beskjed(
        override val id: UUID,
        override val merkelapp: String,
        override val deletedAt: OffsetDateTime?,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
    ) : Notifikasjon

    data class Oppgave(
        override val id: UUID,
        override val merkelapp: String,
        override val deletedAt: OffsetDateTime?,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
        val tilstand: Tilstand,
    ) : Notifikasjon {

        @Suppress("unused")
        /* leses fra database */
        enum class Tilstand {
            NY,
            UTFOERT,
        }
    }

    suspend fun hentNotifikasjon(id: UUID): Notifikasjon?
    suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): Notifikasjon?
    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse)
    suspend fun finnNotifikasjoner(merkelapp: String, antall: Int, offset: Int): List<Notifikasjon>
}

class ProdusentModelImpl(
    private val database: Database
) : ProdusentModel {
    val log = logger()

    private fun Hendelse.BeskjedOpprettet.tilQueryDomene(): ProdusentModel.Beskjed =
        ProdusentModel.Beskjed(
            id = this.id,
            merkelapp = this.merkelapp,
            tekst = this.tekst,
            grupperingsid = this.grupperingsid,
            lenke = this.lenke,
            eksternId = this.eksternId,
            mottaker = this.mottaker,
            opprettetTidspunkt = this.opprettetTidspunkt,
            deletedAt = null,
        )

    private fun Hendelse.OppgaveOpprettet.tilQueryDomene(): ProdusentModel.Oppgave =
        ProdusentModel.Oppgave(
            id = this.id,
            merkelapp = this.merkelapp,
            tekst = this.tekst,
            grupperingsid = this.grupperingsid,
            lenke = this.lenke,
            eksternId = this.eksternId,
            mottaker = this.mottaker,
            opprettetTidspunkt = this.opprettetTidspunkt,
            tilstand = ProdusentModel.Oppgave.Tilstand.NY,
            deletedAt = null,
        )

    override suspend fun hentNotifikasjon(id: UUID): ProdusentModel.Notifikasjon? {
        return database.runNonTransactionalQuery(
            """ select * from notifikasjon where id = ? """, {
                uuid(id)
            }, resultSetTilNotifikasjon
        ).firstOrNull()
    }

    override suspend fun hentNotifikasjon(eksternId: String, merkelapp: String): ProdusentModel.Notifikasjon? {
        return database.runNonTransactionalQuery(
            """ select * from notifikasjon where ekstern_id = ? and merkelapp = ? """, {
                string(eksternId)
                string(merkelapp)
            },
            resultSetTilNotifikasjon
        ).firstOrNull()
    }

    val resultSetTilNotifikasjon: ResultSet.() -> ProdusentModel.Notifikasjon =
        {
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
                )
                else ->
                    throw Exception("Ukjent notifikasjonstype '$type'")
            }
        }

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        val ignored: Unit = when (hendelse) {
            is Hendelse.BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is Hendelse.OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is Hendelse.OppgaveUtført -> oppdatertModellEtterOppgaveUtført(hendelse)
            is Hendelse.BrukerKlikket -> /* Ignorer */ Unit
            is Hendelse.SoftDelete -> oppdaterModellEtterSoftDelete(hendelse)
            is Hendelse.HardDelete -> oppdaterModellEtterHardDelete(hendelse)
        }
    }

    private suspend fun oppdaterModellEtterHardDelete(hardDelete: Hendelse.HardDelete) {
        database.nonTransactionalCommand(
            """
            DELETE FROM notifikasjon 
            WHERE id = ?
            """
        ) {
            uuid(hardDelete.id)
        }
    }

    private suspend fun oppdaterModellEtterSoftDelete(softDelete: Hendelse.SoftDelete) {
        database.nonTransactionalCommand(
            """
            UPDATE notifikasjon
            SET deleted_at = ? 
            WHERE id = ?
            """
        ) {
            timestamptz(softDelete.deletedAt)
            uuid(softDelete.id)
        }
    }

    override suspend fun finnNotifikasjoner(
        merkelapp: String,
        antall: Int,
        offset: Int
    ): List<ProdusentModel.Notifikasjon> {
        return database.runNonTransactionalQuery(
            """ select * from notifikasjon 
                  where merkelapp = ? 
                  limit $antall
                  offset $offset
            """.trimMargin(), {
                string(merkelapp)
            },
            resultSetTilNotifikasjon
        )
    }

    private suspend fun oppdatertModellEtterOppgaveUtført(utførtHendelse: Hendelse.OppgaveUtført) {
        database.nonTransactionalCommand(
            """
            UPDATE notifikasjon
            SET tilstand = '${ProdusentModel.Oppgave.Tilstand.UTFOERT}'
            WHERE id = ?
        """
        ) {
            uuid(utførtHendelse.id)
        }
    }


    private suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: Hendelse.BeskjedOpprettet) {
        val rollbackHandler = { ex: Exception ->
            if (ex is PSQLException && PSQLState.UNIQUE_VIOLATION.state == ex.sqlState) {
                log.error("forsøk på å endre eksisterende beskjed")
            }
            /* TODO: ikke kast exception, hvis vi er sikker på at dette er en duplikat (at-least-once). */
            throw ex
        }

        val nyBeskjed = beskjedOpprettet.tilQueryDomene()

        database.transaction(rollbackHandler) {
            executeCommand(
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
                values ('BESKJED', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json);
            """
            ) {
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

    private suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: Hendelse.OppgaveOpprettet) {
        val rollbackHandler = { ex: Exception ->
            if (ex is PSQLException && PSQLState.UNIQUE_VIOLATION.state == ex.sqlState) {
                log.error("forsøk på å endre eksisterende beskjed")
            }
            /* TODO: ikke kast exception, hvis vi er sikker på at dette er en duplikat (at-least-once). */
            throw ex
        }

        val nyBeskjed = oppgaveOpprettet.tilQueryDomene()

        database.transaction(rollbackHandler) {
            executeCommand(
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
                values ('OPPGAVE', 'NY', ?, ?, ?, ?, ?, ?, ?, ?::json);
            """
            ) {
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
}