package no.nav.arbeidsgiver.notifikasjon

import no.nav.arbeidsgiver.notifikasjon.hendelse.*
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.slf4j.LoggerFactory
import java.time.Instant

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
    val opprettetTidspunkt: Instant,
)

data class Tilgang(
    val virksomhet: String,
    val servicecode: String,
    val serviceedition: String,
)

object QueryModelRepository {
    fun hentNotifikasjoner(fnr: String, tilganger: Collection<Tilgang>): List<QueryBeskjed> {
        return sequence {
            val connection = DB.connection

            val tilgangerJsonB = tilganger.map { "'${objectMapper.writeValueAsString(AltinnMottaker(it.servicecode, it.serviceedition, it.virksomhet))}'" }.joinToString()
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
            """
            )

            prepstat.setString(1, fnr)
            val resultSet = prepstat.executeQuery()
            while (resultSet.next()) {
                yield(
                    QueryBeskjed(
                        merkelapp = resultSet.getString("merkelapp"),
                        tekst = resultSet.getString("tekst"),
                        grupperingsid = resultSet.getString("grupperingsid"),
                        lenke = resultSet.getString("lenke"),
                        eksternId = resultSet.getString("ekstern_id"),
                        mottaker = objectMapper.readValue(
                            resultSet.getString("mottaker"),
                            Mottaker::class.java
                        ),
                        opprettetTidspunkt = Instant.now()
                    )
                )
            }
            resultSet.close()
            connection.close()
        }.toList()
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

fun queryModelBuilderProcessor(event: Event) {
    val koordinat = Koordinat(
        mottaker = event.mottaker,
        merkelapp = event.merkelapp,
        eksternId = event.eksternId
    )
    val nyBeskjed = tilQueryBeskjed(event)

    DB.dataSource.transaction(rollback = {
        if (it is PSQLException && PSQLState.UNIQUE_VIOLATION.state == it.sqlState) {
            log.error("forsøk på å endre eksisterende beskjed")
            throw it // TODO: ta bort. midlertidig for å fremprovosere lagg
        } else {
            throw it
        }
    }) { connection ->
        val prepstat = connection.prepareStatement("""
            insert into notifikasjon(
                koordinat,
                merkelapp,
                tekst,
                grupperingsid,
                lenke,
                ekstern_id,
                mottaker
            )
            values (?, ?, ?, ?, ?, ?, ?::json);
        """)
        prepstat.setString(1, koordinat.toString())
        prepstat.setString(2, nyBeskjed.merkelapp)
        prepstat.setString(3, nyBeskjed.tekst)
        prepstat.setString(4, nyBeskjed.grupperingsid)
        prepstat.setString(5, nyBeskjed.lenke)
        prepstat.setString(6, nyBeskjed.eksternId)
        prepstat.setString(7, objectMapper.writeValueAsString(nyBeskjed.mottaker))
//        prepstat.setObject( 8, nyBeskjed.opprettetTidspunkt) TODO
        prepstat.execute()
    }
}