package no.nav.arbeidsgiver.notifikasjon.graphql

import graphql.GraphQL
import graphql.schema.DataFetcher
import no.nav.arbeidsgiver.notifikasjon.createGraphQL
import no.nav.arbeidsgiver.notifikasjon.repository
import no.nav.arbeidsgiver.notifikasjon.wire

fun brukerGraphQL(): GraphQL =
    createGraphQL("/bruker.graphqls") {

        wire("Notifikasjon") {
            typeResolver { env ->
                val objectTypeName = when (env.getObject<Notifikasjon>()) {
                    is GraphQLBeskjed -> "Beskjed"
                }
                env.schema.getObjectType(objectTypeName)
            }
        }

        wire("Query") {
            dataFetcher("ping") {
                "pong"
            }

            dataFetcher( "notifikasjoner", DataFetcher<List<Notifikasjon>> {
                repository.values.map { queryBeskjed ->
                    GraphQLBeskjed(
                        merkelapp = queryBeskjed.merkelapp,
                        tekst = queryBeskjed.tekst,
                        lenke = queryBeskjed.lenke,
                        opprettetTidspunkt = queryBeskjed.opprettetTidspunkt
                    )
                }
            })
        }
    }

sealed class Notifikasjon

data class GraphQLBeskjed(
    val merkelapp: String,
    val tekst: String,
    val lenke: String,
    val opprettetTidspunkt: String
) : Notifikasjon()