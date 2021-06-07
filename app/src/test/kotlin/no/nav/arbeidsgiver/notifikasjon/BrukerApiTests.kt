package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Brreg
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.BrregEnhet
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

class BrukerApiTests : DescribeSpec({
    val altinn = object : Altinn {
        override suspend fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<QueryModel.Tilgang>()
    }
    val brreg: Brreg = mockk()
    val queryModel: QueryModel = mockk()

    val engine = ktorTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = altinn,
            brreg = brreg,
            queryModelFuture = CompletableFuture.completedFuture(queryModel),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = ProdusentAPI.newGraphQL(
            kafkaProducer = mockk()
        )
    )

    describe("POST bruker-api /api/graphql") {
        context("Query.notifikasjoner") {
            val uuid = UUID.fromString("c39986f2-b31a-11eb-8529-0242ac130003")

            val beskjed = QueryModel.QueryBeskjed(
                merkelapp = "foo",
                tekst = "",
                grupperingsid = "",
                lenke = "",
                eksternId = "",
                mottaker = NærmesteLederMottaker("00000000000", "321", "43"),
                opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
                id = uuid,
                klikketPaa = false
            )
            coEvery {
                queryModel.hentNotifikasjoner(any(), any())
            } returns listOf(beskjed)
            coEvery {
                brreg.hentEnhet("43")
            } returns BrregEnhet("43", "el virksomhete")
            val response = engine.brukerApi(
                """
                    {
                        notifikasjoner {
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
                                id
                                virksomhet  {
                                    virksomhetsnummer
                                    navn
                                }
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
                    it[0].id shouldBe uuid
                    it[0].brukerKlikk.klikketPaa shouldBe false
                }
            }
        }
    }
})

