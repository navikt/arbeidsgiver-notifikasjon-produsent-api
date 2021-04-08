package no.nav.arbeidsgiver.notifikasjon.graphql

import graphql.GraphQL
import no.nav.arbeidsgiver.notifikasjon.QueryModelRepository
import no.nav.arbeidsgiver.notifikasjon.createGraphQL
import no.nav.arbeidsgiver.notifikasjon.hentRettigheter
import no.nav.arbeidsgiver.notifikasjon.wire
import java.time.Instant


data class BrukerContext(
    val fnr:String,
    val token: String
)

fun brukerGraphQL(): GraphQL =
    createGraphQL("/bruker.graphqls") {
        scalar(Scalars.Instant)
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

            dataFetcher( "notifikasjoner") {
                val tilganger = hentRettigheter(it.getContext<BrukerContext>().fnr,it.getContext<BrukerContext>().token)
                val queryBeskjeder = QueryModelRepository.hentNotifikasjoner(
                    it.getContext<BrukerContext>().fnr,
                    tilganger
                )

                queryBeskjeder.map { queryBeskjed ->
                    Beskjed(
                        merkelapp = queryBeskjed.merkelapp,
                        tekst = queryBeskjed.tekst,
                        lenke = queryBeskjed.lenke,
                        opprettetTidspunkt = queryBeskjed.opprettetTidspunkt
                    )
                }
            }
            dataFetcher("whoami"){
                it.getContext<BrukerContext>().fnr
            }
        }
    }

sealed class Notifikasjon

data class Beskjed(
    val merkelapp: String,
    val tekst: String,
    val lenke: String,
    val opprettetTidspunkt: Instant
) : Notifikasjon()