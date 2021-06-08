package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.time.OffsetDateTime
import java.util.*

interface QueryModel {
    data class Tilgang(
        val virksomhet: String,
        val servicecode: String,
        val serviceedition: String,
    )

    data class Beskjed(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
        val id: UUID,
        val klikketPaa: Boolean
    )

    data class Oppgave(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
        val id: UUID,
        val klikketPaa: Boolean
    )

    suspend fun hentNotifikasjoner(fnr: String, tilganger: Collection<Tilgang>): List<Beskjed>
    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse)
    suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String?
    suspend fun oppdaterModellEtterBrukerKlikket(brukerKlikket: Hendelse.BrukerKlikket)
}

class QueryModelImpl(
    private val database: Database
) : QueryModel {
    private val log = logger()

    data class Koordinat(
        val mottaker: Mottaker,
        val merkelapp: String,
        val eksternId: String,
    )

    private fun Hendelse.BeskjedOpprettet.tilQueryDomene(): QueryModel.Beskjed =
        QueryModel.Beskjed(
            id = this.id,
            merkelapp = this.merkelapp,
            tekst = this.tekst,
            grupperingsid = this.grupperingsid,
            lenke = this.lenke,
            eksternId = this.eksternId,
            mottaker = this.mottaker,
            opprettetTidspunkt = this.opprettetTidspunkt,
            klikketPaa = false /* TODO: lag QueryBeskjedMedKlikk, så denne linjen kan fjernes */
        )

    private fun Hendelse.OppgaveOpprettet.tilQueryDomene(): QueryModel.Oppgave =
        QueryModel.Oppgave(
            id = this.id,
            merkelapp = this.merkelapp,
            tekst = this.tekst,
            grupperingsid = this.grupperingsid,
            lenke = this.lenke,
            eksternId = this.eksternId,
            mottaker = this.mottaker,
            opprettetTidspunkt = this.opprettetTidspunkt,
            klikketPaa = false /* TODO: lag QueryBeskjedMedKlikk, så denne linjen kan fjernes */
        )


    private val timer = Health.meterRegistry.timer("query_model_repository_hent_notifikasjoner")

    override suspend fun hentNotifikasjoner(
        fnr: String,
        tilganger: Collection<QueryModel.Tilgang>
    ): List<QueryModel.Beskjed> = timer.coRecord {
        val tilgangerJsonB = tilganger.joinToString {
            "'${
                objectMapper.writeValueAsString(
                    AltinnMottaker(
                        it.servicecode,
                        it.serviceedition,
                        it.virksomhet
                    )
                )
            }'"
        }

        database.runNonTransactionalQuery("""
            select 
                n.*, 
                klikk.notifikasjonsid is not null as klikketPaa
            from notifikasjon as n
            left outer join brukerklikk as klikk on
                klikk.notifikasjonsid = n.id
                and klikk.fnr = ?
            where (
                mottaker ->> '@type' = 'naermesteLeder'
                and mottaker ->> 'naermesteLederFnr' = ?
            ) or (
                mottaker ->> '@type' = 'altinn'
                and mottaker @> ANY (ARRAY [$tilgangerJsonB]::jsonb[])
            )
            order by opprettet_tidspunkt desc
            limit 200
        """, {
            string(fnr)
            string(fnr)
        }) {
            QueryModel.Beskjed(
                merkelapp = getString("merkelapp"),
                tekst = getString("tekst"),
                grupperingsid = getString("grupperingsid"),
                lenke = getString("lenke"),
                eksternId = getString("ekstern_id"),
                mottaker = objectMapper.readValue(getString("mottaker")),
                opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                id = getObject("id", UUID::class.java),
                klikketPaa = getBoolean("klikketPaa")
            )
        }
    }

    override suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: UUID): String? =
            database.runNonTransactionalQuery("""
                SELECT virksomhetsnummer FROM notifikasjonsid_virksomhet_map WHERE notifikasjonsid = ? LIMIT 1
            """, {
                uuid(notifikasjonsid)
            }) {
                getString("virksomhetsnummer")!!
            }.getOrNull(0)

    override suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        when (hendelse) {
            is Hendelse.BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is Hendelse.BrukerKlikket -> oppdaterModellEtterBrukerKlikket(hendelse)
            is Hendelse.OppgaveOpprettet -> oppdaterModellEtterOppgaveOpprettet(hendelse)
            is Hendelse.SlettHendelse -> {
                /* TODO: oppdatere modell? Kaskade-sletting? */
            }
        }
    }

    override suspend fun oppdaterModellEtterBrukerKlikket(brukerKlikket: Hendelse.BrukerKlikket) {
        database.nonTransactionalCommand("""
            INSERT INTO brukerklikk(fnr, notifikasjonsid) VALUES (?, ?)
            ON CONFLICT ON CONSTRAINT brukerklikk_pkey
            DO NOTHING
        """) {
            string(brukerKlikket.fnr)
            uuid(brukerKlikket.notifikasjonsId)
        }
    }

    suspend fun oppdaterModellEtterBeskjedOpprettet(beskjedOpprettet: Hendelse.BeskjedOpprettet) {
        val rollbackHandler = { ex: Exception ->
            if (ex is PSQLException && PSQLState.UNIQUE_VIOLATION.state == ex.sqlState) {
                log.error("forsøk på å endre eksisterende beskjed")
            }
            /* TODO: ikke kast exception, hvis vi er sikker på at dette er en duplikat (at-least-once). */
            throw ex
        }

        val koordinat = Koordinat(
            mottaker = beskjedOpprettet.mottaker,
            merkelapp = beskjedOpprettet.merkelapp,
            eksternId = beskjedOpprettet.eksternId
        )

        val nyBeskjed = beskjedOpprettet.tilQueryDomene()

        database.transaction(rollbackHandler) {
            executeCommand("""
                insert into notifikasjon(
                    koordinat,
                    id,
                    merkelapp,
                    tekst,
                    grupperingsid,
                    lenke,
                    ekstern_id,
                    opprettet_tidspunkt,
                    mottaker
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::json);
            """) {
                string(koordinat.toString())
                uuid(nyBeskjed.id)
                string(nyBeskjed.merkelapp)
                string(nyBeskjed.tekst)
                nullableString(nyBeskjed.grupperingsid)
                string(nyBeskjed.lenke)
                string(nyBeskjed.eksternId)
                timestamptz(nyBeskjed.opprettetTidspunkt)
                string(objectMapper.writeValueAsString(nyBeskjed.mottaker))
            }

            executeCommand("""
                INSERT INTO notifikasjonsid_virksomhet_map(notifikasjonsid, virksomhetsnummer) VALUES (?, ?)
            """) {
                uuid(beskjedOpprettet.id)
                string(beskjedOpprettet.virksomhetsnummer)
            }
        }
    }

    suspend fun oppdaterModellEtterOppgaveOpprettet(oppgaveOpprettet: Hendelse.OppgaveOpprettet) {
        val rollbackHandler = { ex: Exception ->
            if (ex is PSQLException && PSQLState.UNIQUE_VIOLATION.state == ex.sqlState) {
                log.error("forsøk på å endre eksisterende beskjed")
            }
            /* TODO: ikke kast exception, hvis vi er sikker på at dette er en duplikat (at-least-once). */
            throw ex
        }

        val koordinat = Koordinat(
            mottaker = oppgaveOpprettet.mottaker,
            merkelapp = oppgaveOpprettet.merkelapp,
            eksternId = oppgaveOpprettet.eksternId
        )

        val nyBeskjed = oppgaveOpprettet.tilQueryDomene()

        database.transaction(rollbackHandler) {
            executeCommand("""
                insert into notifikasjon(
                    koordinat,
                    id,
                    merkelapp,
                    tekst,
                    grupperingsid,
                    lenke,
                    ekstern_id,
                    opprettet_tidspunkt,
                    mottaker
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::json);
            """) {
                string(koordinat.toString())
                uuid(nyBeskjed.id)
                string(nyBeskjed.merkelapp)
                string(nyBeskjed.tekst)
                nullableString(nyBeskjed.grupperingsid)
                string(nyBeskjed.lenke)
                string(nyBeskjed.eksternId)
                timestamptz(nyBeskjed.opprettetTidspunkt)
                string(objectMapper.writeValueAsString(nyBeskjed.mottaker))
            }

            executeCommand("""
                INSERT INTO notifikasjonsid_virksomhet_map(notifikasjonsid, virksomhetsnummer) VALUES (?, ?)
            """) {
                uuid(oppgaveOpprettet.id)
                string(oppgaveOpprettet.virksomhetsnummer)
            }
        }
    }
}
