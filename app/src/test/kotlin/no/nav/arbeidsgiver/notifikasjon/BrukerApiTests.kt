package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime


fun TestApplicationEngine.brukerApi(req: GraphQLRequest): TestApplicationResponse {
    return post(
        "/api/graphql",
        host = BRUKER_HOST,
        jsonBody = req,
        accept = "application/json",
        authorization = "Bearer $SELVBETJENING_TOKEN"
    )
}

fun TestApplicationEngine.brukerApi(req: String): TestApplicationResponse {
    return brukerApi(GraphQLRequest(req))
}

@ExperimentalTime
class BrukerApiTests : DescribeSpec({
    val altinn = object : Altinn {
        override fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<QueryModel.Tilgang>()
    }

    val queryModel: QueryModel = mockk()

    val engine by ktorEngine(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            queryModelFuture = CompletableFuture.completedFuture(queryModel),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk()
        )
    )

    describe("POST bruker-api /api/graphql") {
        context("Query.notifikasjoner") {
            val beskjed = QueryModel.QueryBeskjedMedId(
                merkelapp = "foo",
                tekst = "",
                grupperingsid = "",
                lenke = "",
                eksternId = "",
                mottaker = FodselsnummerMottaker("00000000000", "43"),
                opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
                id = "1"
            )
            coEvery {
                queryModel.hentNotifikasjoner(any(), any())
            } returns listOf(beskjed)
            val response = engine.brukerApi(
                """
                    {
                        notifikasjoner {
                            ...on Beskjed {
                                klikketPaa
                                lenke
                                tekst
                                merkelapp
                                opprettetTidspunkt
                                id
                            }
                        }
                    }
                """.trimIndent()
            )

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }

            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }

            it("response inneholder riktig data") {
                response.getTypedContent<List<BrukerAPI.Notifikasjon.Beskjed>>("notifikasjoner").let {
                    it shouldNot beEmpty()
                    it[0].merkelapp shouldBe beskjed.merkelapp
                    it[0].id shouldNot beBlank()
                    it[0].id shouldBe "1"
                    it[0].klikketPaa shouldBe false
                }
            }
        }
    }
})

