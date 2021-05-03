package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.transactionAsync
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.useConnectionAsync
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("query-model-builder-processor")

data class Koordinat(
    val mottaker: Mottaker,
    val merkelapp: String,
    val eksternId: String,
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

object QueryModelRepository {
    private val timer = Health.meterRegistry.timer("query_model_repository_hent_notifikasjoner")

    suspend fun hentNotifikasjoner(
        dataSource: DataSource,
        fnr: String,
        tilganger: Collection<Tilgang>
    ): List<QueryBeskjed> =
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

                val prepstat = connection.prepareStatement(
                    """
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
                        """
                )
                prepstat.setString(1, fnr)

                prepstat.executeQuery().use { resultSet ->
                    resultSet.map {
                        QueryBeskjed(
                            merkelapp = resultSet.getString("merkelapp"),
                            tekst = resultSet.getString("tekst"),
                            grupperingsid = resultSet.getString("grupperingsid"),
                            lenke = resultSet.getString("lenke"),
                            eksternId = resultSet.getString("ekstern_id"),
                            mottaker = objectMapper.readValue(resultSet.getString("mottaker")),
                            opprettetTidspunkt = resultSet.getObject(
                                "opprettet_tidspunkt",
                                OffsetDateTime::class.java
                            )
                        )
                    }
                }
            }
        }
}

fun tilQueryBeskjed(event: Event): QueryBeskjed =
    when (event) {
        is BeskjedOpprettet ->
            QueryBeskjed(
                merkelapp = event.merkelapp,
                tekst = event.tekst,
                grupperingsid = event.grupperingsid,
                lenke = event.lenke,
                eksternId = event.eksternId,
                mottaker = event.mottaker,
                opprettetTidspunkt = event.opprettetTidspunkt,
            )
    }

suspend fun queryModelBuilderProcessor(dataSource: DataSource, event: Event) {
    val koordinat = Koordinat(
        mottaker = event.mottaker,
        merkelapp = event.merkelapp,
        eksternId = event.eksternId
    )
    val nyBeskjed = tilQueryBeskjed(event)

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