package no.nav.arbeidsgiver.notifikasjon

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.sql.DataSource

data class BrukerContext(
    val fnr:String,
    val token: String
)

fun createBrukerGraphQL(
    altinn: Altinn,
    dataSourceAsync: CompletableFuture<DataSource>
) = TypedGraphQL<BrukerContext>(
    createGraphQL("/bruker.graphqls") {

        scalar(Scalars.ISO8601DateTime)

        wire("Notifikasjon") {
            typeResolver { env ->
                val objectTypeName = when (env.getObject<Notifikasjon>()) {
                    is Beskjed -> "Beskjed"
                }
                env.schema.getObjectType(objectTypeName)
            }
        }

        wire("Query") {
            dataFetcher("ping") {
                "pong"
            }

            dataFetcher("notifikasjoner") {
                val tilganger = altinn.hentAlleTilganger(
                    it.getContext<BrukerContext>().fnr,
                    it.getContext<BrukerContext>().token
                )
                //GlobalScope.future {
                    QueryModelRepository.hentNotifikasjoner(
                        dataSourceAsync.get(),
                        it.getContext<BrukerContext>().fnr,
                        tilganger
                    ).map { queryBeskjed ->
                        Beskjed(
                            merkelapp = queryBeskjed.merkelapp,
                            tekst = queryBeskjed.tekst,
                            lenke = queryBeskjed.lenke,
                            opprettetTidspunkt = queryBeskjed.opprettetTidspunkt
                        )
                    }
                //}
            }
            dataFetcher("whoami"){
                it.getContext<BrukerContext>().fnr
            }
        }
    }
)

sealed class Notifikasjon

data class Beskjed(
    val merkelapp: String,
    val tekst: String,
    val lenke: String,
    val opprettetTidspunkt: OffsetDateTime
) : Notifikasjon()