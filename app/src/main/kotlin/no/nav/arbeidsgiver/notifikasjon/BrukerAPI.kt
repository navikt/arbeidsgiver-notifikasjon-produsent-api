package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.apache.kafka.clients.producer.Producer
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource

data class BrukerContext(
    val fnr:String,
    val token: String
)

data class NotifikasjonKlikketPaaResultat(
    val errors: List<Nothing>
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
sealed class BrukerMutationError {
    abstract val feilmelding: String
}

data class UgyldigId(override val feilmelding: String) : BrukerMutationError()

fun createBrukerGraphQL(
    altinn: Altinn,
    dataSourceAsync: CompletableFuture<DataSource>,
    kafkaProducer: Producer<KafkaKey, Event>
) = TypedGraphQL<BrukerContext>(
    createGraphQL("/bruker.graphqls") {

        scalar(Scalars.ISO8601DateTime)


        wire("Query") {
            dataFetcher("ping") {
                "pong"
            }

            dataFetcher("notifikasjoner") {
                val tilganger = altinn.hentAlleTilganger(
                    it.getContext<BrukerContext>().fnr,
                    it.getContext<BrukerContext>().token
                )
                // TODO: er det riktig med GlobalScope her eller finnes en bedre måte?
                GlobalScope.future(brukerGraphQLDispatcher) {
                    QueryModelRepository.hentNotifikasjoner(
                        dataSourceAsync.await(),
                        it.getContext<BrukerContext>().fnr,
                        tilganger
                    ).map { queryBeskjed ->
                        Beskjed(
                            merkelapp = queryBeskjed.merkelapp,
                            tekst = queryBeskjed.tekst,
                            lenke = queryBeskjed.lenke,
                            opprettetTidspunkt = queryBeskjed.opprettetTidspunkt,
                            id = queryBeskjed.id
                        )
                    }
                }
            }
            dataFetcher("whoami"){
                it.getContext<BrukerContext>().fnr
            }
        }

        wire("Mutation") {
            dataFetcher("notifikasjonKlikketPaa") {
                val id = it.getTypedArgument<String>("id")

                /* do something */

                NotifikasjonKlikketPaaResultat(
                    errors = listOf()
                )
            }
        }

        subtypes<Notifikasjon>("Notifikasjon") {
            when (it) {
                is Beskjed -> "Beskjed"
            }
        }

        subtypes<BrukerMutationError>("MutationError") {
            when (it) {
                is UgyldigId -> "UgyldigId"
            }
        }
    }
)

sealed class Notifikasjon

data class Beskjed(
    val merkelapp: String,
    val tekst: String,
    val lenke: String,
    val opprettetTidspunkt: OffsetDateTime,
    val id:String
) : Notifikasjon()