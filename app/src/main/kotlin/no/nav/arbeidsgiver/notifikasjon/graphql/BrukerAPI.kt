package no.nav.arbeidsgiver.notifikasjon.graphql

import graphql.GraphQL
import no.nav.arbeidsgiver.notifikasjon.createGraphQL
import no.nav.arbeidsgiver.notifikasjon.dataFetcher
import no.nav.arbeidsgiver.notifikasjon.repository


fun brukerGraphQL(): GraphQL =
    createGraphQL("/bruker.graphqls") {
        dataFetcher("Query", "ping") { "pong" }
        dataFetcher("Query", "notifikasjon") {
            repository.values.map {
                GraphQLBeskjed(
                    merkelapp = it.merkelapp,
                    tekst = it.tekst,
                    lenke = it.lenke,
                    opprettetTidspunkt = it.opprettetTidspunkt
                )
            }
        }
    }

sealed class Notifikasjon {}

data class GraphQLBeskjed(
    val __typename: String = "Beskjed",
    val merkelapp: String,
    val tekst: String,
    val lenke: String,
    val opprettetTidspunkt: String
) : Notifikasjon()