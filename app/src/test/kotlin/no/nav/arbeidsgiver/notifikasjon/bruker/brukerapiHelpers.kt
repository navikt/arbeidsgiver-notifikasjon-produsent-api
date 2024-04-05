package no.nav.arbeidsgiver.notifikasjon.bruker

import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ifNotBlank
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi

/** Denne filen inneholder implementasjoner av graphql-endepunktene, med alle variabler, og som
 * returnerer alle tilgjengelige felter.
 *
 * Brukere av disse funksjonene må være robuste for at nye felter hentes ut. Om man ønsker å
 * GraphQL-funksjonaliteten for å kun hente deler, får man lage egne spørringer for det.
 */


fun TestApplicationEngine.queryNotifikasjonerJson(): TestApplicationResponse = brukerApi("""
    {
        notifikasjoner {
            feilAltinn
            feilDigiSyfo
            notifikasjoner {
                __typename
                ...on Beskjed {
                    brukerKlikk { 
                        __typename
                        id
                        klikketPaa 
                    }
                    lenke
                    tekst
                    merkelapp
                    opprettetTidspunkt
                    sorteringTidspunkt
                    id
                    virksomhet {
                        virksomhetsnummer
                        navn
                    }
                    sak {
                        tittel
                    }
                }
                ...on Oppgave {
                    brukerKlikk { 
                        __typename
                        id
                        klikketPaa 
                    }
                    lenke
                    tilstand
                    tekst
                    merkelapp
                    opprettetTidspunkt
                    sorteringTidspunkt
                    id
                    paaminnelseTidspunkt
                    utfoertTidspunkt
                    utgaattTidspunkt
                    frist
                    virksomhet {
                        virksomhetsnummer
                        navn
                    }
                    sak {
                        tittel
                    }
                }
                ...on Kalenderavtale {
                    brukerKlikk { 
                        __typename
                        id
                        klikketPaa 
                    }
                    lenke
                    avtaletilstand
                    tekst
                    merkelapp
                    opprettetTidspunkt
                    sorteringTidspunkt
                    paaminnelseTidspunkt
                    id
                    startTidspunkt
                    sluttTidspunkt
                    lokasjon {
                        adresse
                        poststed
                        postnummer
                    }
                    digitalt
                    virksomhet {
                        virksomhetsnummer
                        navn
                    }
                    sak {
                        tittel
                    }
                }
            }
        }
    }
""")

fun TestApplicationEngine.querySakstyperJson(): TestApplicationResponse = brukerApi("""
    query {
        sakstyper {
            navn
        }
    }
""".trimIndent())

private data class Parameter(
    val name: String,
    val sort: String,
    val value: Any?,
)

fun TestApplicationEngine.querySakerJson(
    virksomhetsnummer: String? = null,
    virksomhetsnumre: List<String>? = if (virksomhetsnummer == null) listOf(TEST_VIRKSOMHET_1) else null,
    offset: Int? = null,
    limit: Int? = null,
    tekstsoek: String? = null,
    sakstyper: List<String>? = null,
    oppgaveTilstand: List<BrukerAPI.Notifikasjon.Oppgave.Tilstand>? = null,
    sortering: BrukerAPI.SakSortering? = null,
): TestApplicationResponse {
    val parameters = listOf(
        Parameter("virksomhetsnumre", "[String!]", virksomhetsnumre),
        Parameter("virksomhetsnummer", "String", virksomhetsnummer),
        Parameter("offset",  "Int", offset),
        Parameter("limit", "Int", limit),
        Parameter("tekstsoek", "String", tekstsoek),
        Parameter("sakstyper", "[String!]", sakstyper),
        Parameter("oppgaveTilstand", "[OppgaveTilstand!]", oppgaveTilstand),
        Parameter("sortering", "SakSortering", sortering),
    ).filter { (_, _, value) -> value != null }
    val queryParameters = parameters.joinToString(", ") { (navn, sort) -> "\$$navn: $sort" }
        .ifNotBlank { "($it)" }
    val sakerArguments = parameters.joinToString(", ") { (navn) -> "$navn: \$$navn" }
        .ifNotBlank { "($it)" }
    return brukerApi(GraphQLRequest(
        """
    query hentSaker$queryParameters {
        saker$sakerArguments {
            saker {
                id
                tittel
                lenke
                merkelapp
                virksomhet {
                    navn
                    virksomhetsnummer
                }
                sisteStatus {
                    type
                    tekst
                    tidspunkt
                }
                frister
                oppgaver {
                    frist
                    tilstand
                    paaminnelseTidspunkt
                }
                tidslinje {
                    __typename
                    ...on OppgaveTidslinjeElement {
                        id
                        tekst
                        tilstand
                        opprettetTidspunkt
                        paaminnelseTidspunkt
                        utgaattTidspunkt
                        utfoertTidspunkt
                        frist
                    }
                    ...on BeskjedTidslinjeElement {
                        id
                        tekst
                        opprettetTidspunkt
                    }
                    ... on KalenderavtaleTidslinjeElement {
                        id
                        tekst
                        avtaletilstand
                        startTidspunkt
                        sluttTidspunkt
                        lokasjon {
                            adresse
                            postnummer
                            poststed                        
                        }
                        digitalt
                    }
                }
            }
            sakstyper {
                navn
                antall
            }
            oppgaveTilstandInfo {
                tilstand
                antall
            }
            feilAltinn
            totaltAntallSaker
        }
    }
    """.trimIndent(),
        "hentSaker",
        parameters.associate { (navn, _, value) ->
            navn to value
        }
    ))
}


fun TestApplicationEngine.queryKommendeKalenderavtalerJson(
    virksomhetsnumre: List<String> = listOf(TEST_VIRKSOMHET_1),
): TestApplicationResponse = brukerApi(
    GraphQLRequest(
        """
        query kommendeKalenderavtaler(${"$"}virksomhetsnumre: [String!]!) {
            kommendeKalenderavtaler(virksomhetsnumre: ${"$"}virksomhetsnumre) {
                feilAltinn
                feilDigiSyfo
                avtaler {
                    __typename
                    brukerKlikk { 
                        __typename
                        id
                        klikketPaa 
                    }
                    lenke
                    avtaletilstand
                    tekst
                    merkelapp
                    opprettetTidspunkt
                    sorteringTidspunkt
                    id
                    startTidspunkt
                    sluttTidspunkt
                    lokasjon {
                        adresse
                        poststed
                        postnummer
                    }
                    digitalt
                    virksomhet {
                        virksomhetsnummer
                        navn
                    }
                    sak {
                        tittel
                    }        
                }
            }
        }
        """,
        "kommendeKalenderavtaler",
        mapOf("virksomhetsnumre" to virksomhetsnumre)
    )
)