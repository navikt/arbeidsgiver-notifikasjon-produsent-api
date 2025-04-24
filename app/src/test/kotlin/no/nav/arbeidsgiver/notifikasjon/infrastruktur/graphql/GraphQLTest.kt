package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import kotlin.test.Test
import kotlin.test.assertEquals


class GraphQLTest {
    @Test
    fun `GraphQLRequest queryName`() {
        with(
            GraphQLRequest(
                """
                query {
                  whoami
                }
                """
            )
        ) {
            assertEquals("whoami", queryName)
        }

        with(
            GraphQLRequest(
                """
                mutation (${'$'}id: ID!) {
                  hardDeleteSak(id: ${'$'}id) {
                    __typename
                    ... on HardDeleteSakVellykket {
                      id
                    }
                  }
                }
                """
            )
        ) {
            assertEquals("hardDeleteSak", queryName)
        }

        with(
            GraphQLRequest(
                """
                mutation Foo(
                    id: ID!
                    lol: String
                ) {
                  bar(
                    id: id
                    lol: lol
                  ) {
                    __typename
                  }
                }
                """
            )
        ) {
            assertEquals("bar", queryName)
        }

        with(
            GraphQLRequest(
                """
                mutation { hardDeleteSak(id: "42") {
                    __typename
                    ... on HardDeleteSakVellykket {
                      id
                    }
                  }
                }
                """
            )
        ) {
            assertEquals("hardDeleteSak", queryName)
        }

        with(
            GraphQLRequest(
                """
                query {
                  hentNotifikasjon(id: "42") {
                    __typename
                    ... on HentetNotifikasjon {
                      id
                    }
                  }
                }
                """
            )
        ) {
            assertEquals("hentNotifikasjon", queryName)
        }

        with(
            GraphQLRequest(
                """
                mutation OpprettNyOppgave(
                    eksternId: String!
                    lenke: String!,
                    tekst: String!,
                    virksomhetsnummer: String!,
                    merkelapp: String!,
                    tidspunkt: ISO8601DateTime
                ){
                nyOppgave(nyOppgave: {
                    mottakere: [
                        {
                            altinn: {
                                serviceCode: "4936"
                                serviceEdition: "1"
                            }
                        }
                    ]
                    notifikasjon: {
                        lenke: "",
                    }
                    metadata: {
                        virksomhetsnummer: "",
                    }
                }) {
                    __typename
                    ... on NyOppgaveVellykket {
                        id
                    }
                    ... on UgyldigMerkelapp {
                        feilmelding
                    }
                    ... on UgyldigMottaker {
                        feilmelding
                    }
                    ... on DuplikatEksternIdOgMerkelapp {
                        feilmelding
                    }
                    ... on UkjentProdusent {
                        feilmelding
                    }
                    ... on UkjentRolle {
                        feilmelding
                    }
                }
            }
            """
            )
        ) {
            assertEquals("nyOppgave", queryName)
        }
    }
}
