package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GraphQLTest : DescribeSpec({
    describe("GraphQLRequest.queryName") {
        GraphQLRequest("""
            query {
              whoami
            }
        """.trimIndent()).queryName shouldBe "whoami"
        GraphQLRequest("""
            mutation (${'$'}id: ID!) {
              hardDeleteSak(id: ${'$'}id) {
                __typename
                ... on HardDeleteSakVellykket {
                  id
                }
              }
            }
        """.trimIndent()).queryName shouldBe "hardDeleteSak"
        GraphQLRequest("""
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
        """.trimIndent()).queryName shouldBe "bar"

        GraphQLRequest("""
            mutation { hardDeleteSak(id: "42") {
                __typename
                ... on HardDeleteSakVellykket {
                  id
                }
              }
            }
        """.trimIndent()).queryName shouldBe "hardDeleteSak"

        GraphQLRequest("""
            query {
              hentNotifikasjon(id: "42") {
                __typename
                ... on HentetNotifikasjon {
                  id
                }
              }
            }
        """.trimIndent()).queryName shouldBe "hentNotifikasjon"

        GraphQLRequest("""
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
        """.trimIndent()).queryName shouldBe "nyOppgave"
    }
})
