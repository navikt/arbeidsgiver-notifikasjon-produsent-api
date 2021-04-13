package no.nav.arbeidsgiver.notifikasjon.graphql

import graphql.GraphQL
import no.nav.arbeidsgiver.notifikasjon.*
import java.time.Instant


data class BrukerContext(
    val fnr:String
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
                val tilganger = listOf(
                    Tilgang(
                        virksomhet = "910825631", /* akkarvik */
                        servicecode = "5278", /* tilskudd */
                        serviceedition = "1"
                    ),
                    Tilgang(
                        virksomhet = "910825631", /* akkarvik */
                        servicecode = "5332", /* arbeidstrening */
                        serviceedition = "1"
                    ),
                    Tilgang(
                        virksomhet = "910953494", /* haukedalen */
                        servicecode = "5332", /* arbeidstrening */
                        serviceedition = "1"
                    )
                )

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