package no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GraphQLTest : DescribeSpec({
    describe("GraphQLRequest.queryName") {
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
    }
})
