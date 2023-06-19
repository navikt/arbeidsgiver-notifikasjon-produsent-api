package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Metrics.meterRegistry
import no.nav.arbeidsgiver.notifikasjon.util.brukerApi
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import java.time.OffsetDateTime
import java.util.*

class BrukerApiTests : DescribeSpec({
    val queryModel: BrukerRepositoryImpl = mockk()

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
    )

    describe("graphql bruker-api") {
        context("Query.notifikasjoner") {

            val beskjed = BrukerModel.Beskjed(
                merkelapp = "foo",
                tekst = "",
                grupperingsid = "",
                lenke = "",
                eksternId = "",
                virksomhetsnummer = "43",
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
                virksomhetsnummer = "43",
                opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
                utgaattTidspunkt = null,
                id = UUID.fromString("c39986f2-b31a-11eb-8529-0242ac130005"),
                klikketPaa = false,
                frist = null,
                paaminnelseTidspunkt = null,
                utfoertTidspunkt = null,
            )

            coEvery {
                queryModel.hentNotifikasjoner(any(), any())
            } returns listOf(beskjed, oppgave)

            val response = engine.brukerApi(
                """
                    {
                        notifikasjoner{
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
                                    virksomhet {
                                        virksomhetsnummer
                                        navn
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            )

            it("response inneholder riktig data") {
                response.getTypedContent<List<BrukerAPI.Notifikasjon>>("notifikasjoner/notifikasjoner").let {
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

            it("notifikasjoner hentet counter økt") {
                val vellykket = meterRegistry.get("notifikasjoner_hentet").counter().count()
                vellykket shouldBe 2
            }
        }
    }
})

