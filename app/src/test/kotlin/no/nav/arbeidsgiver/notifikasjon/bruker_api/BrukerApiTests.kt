package no.nav.arbeidsgiver.notifikasjon.bruker_api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModelImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.OffsetDateTime
import java.util.*

class BrukerApiTests : DescribeSpec({
    val queryModel: BrukerModelImpl = mockk()

    val engine = ktorBrukerTestServer(
        brukerGraphQL = BrukerAPI.createBrukerGraphQL(
            altinn = AltinnStub(),
            brreg = BrregStub("43" to "el virksomhete"),
            brukerModel = queryModel,
            kafkaProducer = mockk(),
            nærmesteLederService = NærmesteLederServiceStub(),
        )
    )

    describe("graphql bruker-api") {
        context("Query.notifikasjoner") {

            val beskjed = BrukerModel.Beskjed(
                merkelapp = "foo",
                tekst = "",
                grupperingsid = "",
                lenke = "",
                eksternId = "",
                mottaker = NærmesteLederMottaker("00000000000", "321", "43"),
                opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
                id = UUID.fromString("c39986f2-b31a-11eb-8529-0242ac130003"),
                klikketPaa = false
            )

            val oppgave = BrukerModel.Oppgave(
                tilstand = BrukerModel.Oppgave.Tilstand.NY,
                merkelapp = "foo",
                tekst = "",
                grupperingsid = "",
                lenke = "",
                eksternId = "",
                mottaker = NærmesteLederMottaker("00000000000", "321", "43"),
                opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
                id = UUID.fromString("c39986f2-b31a-11eb-8529-0242ac130005"),
                klikketPaa = false
            )

            coEvery {
                queryModel.hentNotifikasjoner(any(), any(), any())
            } returns listOf(beskjed, oppgave)

            val response = engine.brukerApi(
                """
                    {
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
                                id
                                virksomhet {
                                    virksomhetsnummer
                                    navn
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
                                id
                                virksomhet {
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
                response.getTypedContent<List<BrukerAPI.Notifikasjon>>("notifikasjoner").let {
                    it shouldNot beEmpty()
                    val returnedBeskjed = it[0] as BrukerAPI.Notifikasjon.Beskjed
                    returnedBeskjed.merkelapp shouldBe beskjed.merkelapp
                    returnedBeskjed.id shouldBe beskjed.id
                    returnedBeskjed.brukerKlikk.klikketPaa shouldBe false

                    val returnedOppgave = it[1] as BrukerAPI.Notifikasjon.Oppgave
                    returnedOppgave.merkelapp shouldBe oppgave.merkelapp
                    returnedOppgave.id shouldBe oppgave.id
                    returnedOppgave.brukerKlikk.klikketPaa shouldBe false
                }
            }
        }
    }
})

