package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.time.OffsetDateTime

class QueryModel(
    private val database: Database
) {
    private val log = logger()

    data class Koordinat(
        val mottaker: Mottaker,
        val merkelapp: String,
        val eksternId: String,
    )

    data class QueryBeskjedMedId(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
        val id: String
    )

    data class QueryBeskjed(
        val merkelapp: String,
        val tekst: String,
        val grupperingsid: String? = null,
        val lenke: String,
        val eksternId: String,
        val mottaker: Mottaker,
        val opprettetTidspunkt: OffsetDateTime,
    )

    private fun Hendelse.BeskjedOpprettet.tilQueryDomene(): QueryBeskjed =
        QueryBeskjed(
            merkelapp = this.merkelapp,
            tekst = this.tekst,
            grupperingsid = this.grupperingsid,
            lenke = this.lenke,
            eksternId = this.eksternId,
            mottaker = this.mottaker,
            opprettetTidspunkt = this.opprettetTidspunkt,
        )

    data class Tilgang(
        val virksomhet: String,
        val servicecode: String,
        val serviceedition: String,
    )

    private val timer = Health.meterRegistry.timer("query_model_repository_hent_notifikasjoner")

    suspend fun hentNotifikasjoner(
        fnr: String,
        tilganger: Collection<Tilgang>
    ): List<QueryBeskjedMedId> = timer.coRecord {
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
            select * from notifikasjon
            where (
                mottaker ->> '@type' = 'fodselsnummer'
                and mottaker ->> 'fodselsnummer' = ?
            ) 
            or (
                mottaker ->> '@type' = 'altinn'
                and mottaker @> ANY (ARRAY [$tilgangerJsonB]::jsonb[]))
            order by opprettet_tidspunkt desc
            limit 50
        """, {
            setString(1, fnr)
        }) {
            QueryBeskjedMedId(
                merkelapp = getString("merkelapp"),
                tekst = getString("tekst"),
                grupperingsid = getString("grupperingsid"),
                lenke = getString("lenke"),
                eksternId = getString("ekstern_id"),
                mottaker = objectMapper.readValue(getString("mottaker")),
                opprettetTidspunkt = getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                id = getString("id")
            )
        }
    }

    suspend fun virksomhetsnummerForNotifikasjon(notifikasjonsid: String): String? =
            database.runNonTransactionalQuery("""
                SELECT virksomhetsnummer FROM notifikasjonsid_virksomhet_map WHERE notifikasjonsid = ? LIMIT 1
            """, {
                setString(1, notifikasjonsid)
            }) {
                getString("virksomhetsnummer")!!
            }.getOrNull(0)

    suspend fun oppdaterModellEtterHendelse(hendelse: Hendelse) {
        when (hendelse) {
            is Hendelse.BeskjedOpprettet -> oppdaterModellEtterBeskjedOpprettet(hendelse)
            is Hendelse.BrukerKlikket -> oppdaterModellEtterBrukerKlikket(hendelse)
        }
    }

    suspend fun oppdaterModellEtterBrukerKlikket(brukerKlikket: Hendelse.BrukerKlikket) {
        database.nonTransactionalCommand("""
            INSERT INTO brukerklikk(fnr, notifikasjonsid) VALUES (?, ?)
            ON CONFLICT ON CONSTRAINT brukerklikket_unique
            DO NOTHING
        """) {
            setString(1, brukerKlikket.fnr)
            setString(2, brukerKlikket.notifikasjonsId)
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
                    merkelapp,
                    tekst,
                    grupperingsid,
                    lenke,
                    ekstern_id,
                    opprettet_tidspunkt,
                    mottaker
                )
                values (?, ?, ?, ?, ?, ?, ?, ?::json);
            """) {
                setString(1, koordinat.toString())
                setString(2, nyBeskjed.merkelapp)
                setString(3, nyBeskjed.tekst)
                setString(4, nyBeskjed.grupperingsid)
                setString(5, nyBeskjed.lenke)
                setString(6, nyBeskjed.eksternId)
                setObject(7, nyBeskjed.opprettetTidspunkt)
                setString(8, objectMapper.writeValueAsString(nyBeskjed.mottaker))
            }

            executeCommand("""
                INSERT INTO notifikasjonsid_virksomhet_map(notifikasjonsid, virksomhetsnummer) VALUES (?, ?)
            """) {
                setString(1, beskjedOpprettet.guid.toString())
                setString(2, beskjedOpprettet.virksomhetsnummer)
            }
        }
    }
}
