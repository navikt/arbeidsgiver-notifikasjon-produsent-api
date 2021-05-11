package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.map
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.transaction
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.useConnection
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import java.time.OffsetDateTime
import javax.sql.DataSource

object QueryModel {
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

    data class Tilgang(
        val virksomhet: String,
        val servicecode: String,
        val serviceedition: String,
    )

    private val timer = Health.meterRegistry.timer("query_model_repository_hent_notifikasjoner")

    suspend fun hentNotifikasjoner(
        dataSource: DataSource,
        fnr: String,
        tilganger: Collection<Tilgang>
    ): List<QueryBeskjedMedId> =
        dataSource.useConnection { connection ->
            timer.recordCallable {
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

                val prepstat = connection.prepareStatement("""
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
                """)
                prepstat.setString(1, fnr)

                prepstat.executeQuery().use { resultSet ->
                    resultSet.map {
                        QueryBeskjedMedId(
                            merkelapp = resultSet.getString("merkelapp"),
                            tekst = resultSet.getString("tekst"),
                            grupperingsid = resultSet.getString("grupperingsid"),
                            lenke = resultSet.getString("lenke"),
                            eksternId = resultSet.getString("ekstern_id"),
                            mottaker = objectMapper.readValue(resultSet.getString("mottaker")),
                            opprettetTidspunkt = resultSet.getObject("opprettet_tidspunkt", OffsetDateTime::class.java),
                            id = resultSet.getString("id")
                        )
                    }
                }
            }
        }

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

    suspend fun builderProcessor(dataSource: DataSource, hendelse: Hendelse) {
        when (hendelse) {
            is Hendelse.BeskjedOpprettet -> builderProcessor(dataSource, hendelse)
            is Hendelse.BrukerKlikket -> TODO()
        }
    }

    suspend fun builderProcessor(dataSource: DataSource, beskjedOpprettet: Hendelse.BeskjedOpprettet) {
        val koordinat = Koordinat(
            mottaker = beskjedOpprettet.mottaker,
            merkelapp = beskjedOpprettet.merkelapp,
            eksternId = beskjedOpprettet.eksternId
        )
        val nyBeskjed = beskjedOpprettet.tilQueryDomene()

        dataSource.transaction(rollback = {
            if (it is PSQLException && PSQLState.UNIQUE_VIOLATION.state == it.sqlState) {
                log.error("forsøk på å endre eksisterende beskjed")
            } else {
                throw it
            }
        }) { connection ->
            val prepstat = connection.prepareStatement(
                """
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
        """
            )
            prepstat.setString(1, koordinat.toString())
            prepstat.setString(2, nyBeskjed.merkelapp)
            prepstat.setString(3, nyBeskjed.tekst)
            prepstat.setString(4, nyBeskjed.grupperingsid)
            prepstat.setString(5, nyBeskjed.lenke)
            prepstat.setString(6, nyBeskjed.eksternId)
            prepstat.setObject(7, nyBeskjed.opprettetTidspunkt)
            prepstat.setString(8, objectMapper.writeValueAsString(nyBeskjed.mottaker))
            prepstat.execute()
        }
    }
}
